{
  description = "PixelXpert (fork) - reproducible toolchain to build the signed APK + flashable Magisk zip";

  inputs = {
    # Pinned to the exact rev the sibling Continuum+ flake already verified builds an
    # Android compileSdk-36 project on NixOS, so this toolchain is known-good out of the gate.
    nixpkgs.url = "github:NixOS/nixpkgs/e9a7635a57597d9754eccebdfc7045e6c8600e6b";
  };

  outputs = { self, nixpkgs }:
    let
      # androidenv (and therefore this build) only makes sense on Linux/macOS.
      systems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f system);

      # --- SDK component versions (all verified present in the pinned nixpkgs) ---
      # compileSdk/minSdk/targetSdk are all 36 (app/build.gradle.kts) -> platform 36.
      # AGP 9.1.0's default build-tools is 36.0.0; 35.0.0 kept as a safety net.
      platformVersions    = [ "35" "36" ];
      buildToolsVersions  = [ "35.0.0" "36.0.0" ];
      aapt2BuildTools     = "36.0.0"; # which build-tools' aapt2 to feed AGP (see below)
      cmdLineToolsVersion = "19.0";
      platformToolsVersion = "36.0.2";

      mkPkgs = system: import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true; # the Android SDK is unfree
          android_sdk.accept_license = true;
        };
      };

      mkToolchain = system:
        let
          pkgs = mkPkgs system;

          androidComposition = pkgs.androidenv.composeAndroidPackages {
            inherit cmdLineToolsVersion platformToolsVersion
              buildToolsVersions platformVersions;
            includeNDK = false; # no native C/C++ in this project; the arm64-v8a abiFilter
                                # just repackages prebuilt .so files from AAR deps (pytorch, mlkit).
            includeEmulator = false;
            includeSystemImages = false;
            includeSources = false;
          };

          # AGP 9.x runs on JDK 17+; the project targets Java/Kotlin 17 bytecode. JDK 21 builds it cleanly.
          jdk = pkgs.jdk21;
          sdkRoot = "${androidComposition.androidsdk}/libexec/android-sdk";
          # AGP otherwise downloads its own aapt2 from Maven (a prebuilt ELF that
          # won't run on NixOS). Point it at the autoPatchelf'd SDK binary instead.
          aapt2 = "${sdkRoot}/build-tools/${aapt2BuildTools}/aapt2";
        in
        { inherit pkgs androidComposition jdk sdkRoot aapt2; };

      # Shared shell prologue: validates the checkout, wires the Nix toolchain, points Gradle at the
      # Nix SDK, and sets up signing. Sourced by both the APK and zip build apps.
      commonPrologue = { jdk, sdkRoot }: ''
        # Operate on the user's real checkout (the build mutates the working tree:
        # local.properties, app/build/..., and -- for the zip build -- version.properties).
        PROJECT_ROOT="''${PROJECT_ROOT:-$PWD}"
        if [ ! -f "$PROJECT_ROOT/settings.gradle.kts" ] || [ ! -x "$PROJECT_ROOT/gradlew" ]; then
          echo "error: not a PixelXpert checkout (no settings.gradle.kts / gradlew in $PROJECT_ROOT)." >&2
          echo "       Run from the repo root, or set PROJECT_ROOT=/path/to/PixelXpert." >&2
          exit 1
        fi
        cd "$PROJECT_ROOT"

        # --- Nix-provided toolchain ---
        export JAVA_HOME="${jdk.home}"
        export ANDROID_HOME="${sdkRoot}"
        export ANDROID_SDK_ROOT="${sdkRoot}"
        export PATH="$JAVA_HOME/bin:$PATH"

        echo "[*] JDK:         ${jdk.home}"
        echo "[*] Android SDK: ${sdkRoot}"

        # --- Point Gradle at the Nix SDK (only if not already pinned) ---
        if [ ! -f local.properties ]; then
          echo "sdk.dir=${sdkRoot}" > local.properties
          echo "[*] wrote local.properties (sdk.dir -> Nix SDK)"
        elif ! grep -q "^sdk.dir=${sdkRoot}$" local.properties; then
          echo "[WARNING] local.properties already pins a different sdk.dir - the Nix SDK at" >&2
          echo "          ${sdkRoot} will NOT be used. Remove local.properties to use it." >&2
        fi

        # --- Signing config (NO secrets baked into this flake) ---
        # Precedence: an existing ReleaseKey.properties wins (the wired local setup); otherwise build
        # one from PIXELXPERT_* env vars (the nix-CI path); otherwise warn and let AGP fall back to the
        # debug key (app/build.gradle.kts does this gracefully). The keystore file + passwords live only
        # in a local (gitignored) ReleaseKey.properties / .jks or in the environment -- never in git.
        if [ -f ReleaseKey.properties ]; then
          echo "[*] using existing ReleaseKey.properties (release signing)"
        elif [ -n "''${PIXELXPERT_KEYSTORE_PASSWORD:-}" ] && [ -n "''${PIXELXPERT_KEY_PASSWORD:-}" ]; then
          cat > ReleaseKey.properties <<EOF
        storeFile=''${PIXELXPERT_KEYSTORE_FILE:-ReleaseKey.jks}
        storePassword=''${PIXELXPERT_KEYSTORE_PASSWORD}
        keyAlias=''${PIXELXPERT_KEY_ALIAS:-fork}
        keyPassword=''${PIXELXPERT_KEY_PASSWORD}
        EOF
          echo "[*] wrote ReleaseKey.properties from PIXELXPERT_* env vars (release signing)"
        else
          echo "[WARNING] no signing config found (ReleaseKey.properties / PIXELXPERT_* env vars)." >&2
          echo "          The release APK will be DEBUG-signed -- fine for local testing, but a" >&2
          echo "          debug-signed APK cannot update a release-signed install." >&2
        fi
      '';

      # `nix run` (default) -- builds the signed release APK only. Does NOT bump the version
      # (assembleRelease reads version.properties; the increment task is not in this graph), so it is
      # safe for quick on-device test builds. Extra args are forwarded via "$@".
      mkBuildApk = system:
        let inherit (mkToolchain system) pkgs jdk sdkRoot aapt2;
        in pkgs.writeShellApplication {
          name = "pixelxpert-build";
          runtimeInputs = [ jdk pkgs.coreutils pkgs.gnugrep pkgs.findutils ];
          text = ''
            ${commonPrologue { inherit jdk sdkRoot; }}

            echo "[*] Building signed release APK (first run fetches Gradle + deps over the network)..."
            ./gradlew :app:assembleRelease \
              -Pandroid.aapt2FromMavenOverride="${aapt2}" \
              --no-daemon --stacktrace "$@"

            out="app/build/outputs/apk/release"
            echo
            echo "[OK] Build complete. Release APK in $out/:"
            find "$out" -maxdepth 1 -name '*.apk' -printf '   %f\n' | sort
          '';
        };

      # `nix run .#zip` -- the canonical release build: bumps the canary version, assembles the signed
      # APK, and packages the flashable Magisk zip (mirrors CI / CLAUDE.md). Mutates version.properties
      # + module.prop + the latest*.json OTA descriptors. Extra args forwarded via "$@".
      mkBuildZip = system:
        let inherit (mkToolchain system) pkgs jdk sdkRoot aapt2;
        in pkgs.writeShellApplication {
          name = "pixelxpert-build-zip";
          runtimeInputs = [ jdk pkgs.coreutils pkgs.gnugrep pkgs.findutils ];
          text = ''
            ${commonPrologue { inherit jdk sdkRoot; }}

            echo "[*] Building flashable Magisk zip (buildCanary: increments version + assembles + zips)..."
            ./gradlew buildCanary -Pchannel=canary \
              -Pandroid.aapt2FromMavenOverride="${aapt2}" \
              --no-daemon --stacktrace "$@"

            echo
            echo "[OK] Build complete:"
            [ -f output/PixelXpert.zip ]        && echo "   flashable zip: output/PixelXpert.zip"
            [ -f app/build/distApk/PixelXpert.apk ] && echo "   apk:           app/build/distApk/PixelXpert.apk"
          '';
        };

      mkDevShell = system:
        let inherit (mkToolchain system) pkgs jdk sdkRoot aapt2;
        in pkgs.mkShell {
          packages = [ jdk pkgs.gradle ];
          ANDROID_HOME = sdkRoot;
          ANDROID_SDK_ROOT = sdkRoot;
          JAVA_HOME = jdk.home;
          # Same aapt2 fix as the build apps; export so `./gradlew` works in the shell.
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${aapt2}";
          shellHook = ''
            echo "PixelXpert dev shell - JDK 21 + Android SDK (compileSdk 36)."
            echo "Build the APK with:           ./gradlew :app:assembleRelease   (or: nix run)"
            echo "Build the flashable zip with: ./gradlew buildCanary -Pchannel=canary   (or: nix run .#zip)"
          '';
        };
    in
    {
      apps = forAllSystems (system:
        let
          build = { type = "app"; program = "${mkBuildApk system}/bin/pixelxpert-build"; };
          zip = { type = "app"; program = "${mkBuildZip system}/bin/pixelxpert-build-zip"; };
        in { inherit build zip; default = build; });

      devShells = forAllSystems (system: { default = mkDevShell system; });

      # Exposed for debugging / prefetching the toolchain (`nix build .#androidSdk`).
      packages = forAllSystems (system:
        let tc = mkToolchain system;
        in {
          androidSdk = tc.androidComposition.androidsdk;
          buildScript = mkBuildApk system;
        });
    };
}
