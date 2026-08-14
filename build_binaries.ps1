# Function to build for android using gomobile
function Build-Android {
    $LibDir = "app\libs"
	$GoPath = (go env GOPATH).Trim()
	$Gomobile = if ($env:GOMOBILE_EXE) { $env:GOMOBILE_EXE } else { Join-Path $GoPath "bin\gomobile.exe" }
	$SdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "D:\AndroidSDK" }
	$env:ANDROID_HOME = $SdkRoot
	$env:ANDROID_SDK_ROOT = $SdkRoot
	if (-not $env:GOPROXY) {
		$env:GOPROXY = "https://goproxy.cn,direct"
	}

    if (!(Test-Path -Path $LibDir)) {
        New-Item -ItemType Directory -Path $LibDir -Force
    }

    Write-Host "Building Android AAR (gomobile bind) for gobridge to $LibDir\gonctool.aar..."

    # Ensure we are in the root directory context where gomobile can work best, usually pointing to the package
    # Since gobridge is a sub-module, we need to correct the import path or run from context
    
    if (!(Test-Path -LiteralPath $Gomobile -PathType Leaf)) {
        throw "gomobile was not found at $Gomobile"
    }
    $GoMobileBin = Split-Path -Parent $Gomobile
    $env:PATH = "$GoMobileBin;$env:PATH"
    if (!(Test-Path -LiteralPath "$GoMobileBin\gobind.exe" -PathType Leaf)) {
        throw "gobind was not found at $GoMobileBin\gobind.exe; run gomobile init first"
    }

    # Set ANDROID_NDK_HOME explicitly if not set, trying to find it in the configured SDK.
    if (-not $env:ANDROID_NDK_HOME) {
        $PossibleNdk = Get-ChildItem -Path "$SdkRoot\ndk" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
        if ($PossibleNdk) {
            $env:ANDROID_NDK_HOME = $PossibleNdk.FullName
            Write-Host "Setting ANDROID_NDK_HOME to $env:ANDROID_NDK_HOME"
        }
        else {
            throw "Could not find an Android NDK in $SdkRoot\ndk"
        }
    }
    else {
        Write-Host "ANDROID_NDK_HOME is set to $env:ANDROID_NDK_HOME"
    }

    Push-Location gobridge
    # gomobile bind -target=android -o ..\$LibDir\gonctool.aar .
    # Note: gomobile bind automatically builds for all supported Android architectures (arm, arm64, 386, amd64) by default or specified by -target
    
    # We need to make sure 'gobridge' module is tidy or correct. 
    # Just in case, let's run mod tidy
    try {
        go mod tidy
        if ($LASTEXITCODE -ne 0) {
            throw "go mod tidy failed with exit code $LASTEXITCODE"
        }
    
    # Specify -androidapi 21 to ensure compatibility with modern NDKs that dropped support for older APIs (project requires 21+)
        & $Gomobile bind -target=android -androidapi 21 -o ..\app\libs\gonctool.aar .
        if ($LASTEXITCODE -ne 0) {
            throw "gomobile bind failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }

    if (!(Test-Path -LiteralPath "$LibDir\gonctool.aar" -PathType Leaf)) {
        throw "gomobile reported success but $LibDir\gonctool.aar was not created"
    }
    Write-Host "Build complete: app\libs\gonctool.aar"
}

# Build for Android
Build-Android
