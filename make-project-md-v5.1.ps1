#requires -Version 5.1
<#
Create a readable, UTF-8 (no BOM) Markdown snapshot of project text sources.
Never modifies source files and never overwrites an existing snapshot.

Examples:
  .\make-project-md-v5.ps1
  .\make-project-md-v5.ps1 -ProjectPath C:\ai-drone -IncludeApkText
  .\make-project-md-v5.ps1 -AllowCp949 -IncludeArtifacts
#>
[CmdletBinding()]
param(
    [string]$ProjectPath = $PSScriptRoot,
    [string]$OutputPath,
    [switch]$IncludeApkText,
    [switch]$IncludeArtifacts,
    [switch]$AllowCp949,
    [ValidateRange(1, 1024)]
    [int]$MaxFileSizeMB = 8
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ProjectPath)) {
    $ProjectPath = (Get-Location).Path
}
$projectDir = (Resolve-Path -LiteralPath $ProjectPath).ProviderPath
if ($projectDir.Length -gt 3) { $projectDir = $projectDir.TrimEnd('\', '/') }

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $OutputPath = Join-Path $projectDir "project-source-$stamp.md"
} elseif (-not [System.IO.Path]::IsPathRooted($OutputPath)) {
    $OutputPath = Join-Path $projectDir $OutputPath
}
$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
$reportPath = [System.IO.Path]::ChangeExtension($outputFullPath, '.report.json')

# Fail instead of deleting or overwriting a previous source snapshot.
if ((Test-Path -LiteralPath $outputFullPath) -or
    (Test-Path -LiteralPath $reportPath)) {
    throw "Output already exists: $outputFullPath (or its report). Choose a new output path."
}
$outputDir = [System.IO.Path]::GetDirectoryName($outputFullPath)
if (-not (Test-Path -LiteralPath $outputDir -PathType Container)) {
    throw "Output directory does not exist: $outputDir"
}

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$utf8Strict = [System.Text.UTF8Encoding]::new($false, $true)
$utf16LeStrict = [System.Text.UnicodeEncoding]::new($false, $true, $true)
$utf16BeStrict = [System.Text.UnicodeEncoding]::new($true, $true, $true)

$excludeDirs = @(
    '.git', '.idea', '.vscode', '.gradle', '.next', '.cache',
    '__pycache__', '.venv', 'venv', 'node_modules',
    'build', 'dist', 'target', 'out', 'coverage', 'jadx-full',
    'TripPamineAI'
)
if (-not $IncludeApkText) { $excludeDirs += 'apk' }
if (-not $IncludeArtifacts) { $excludeDirs += 'artifacts' }

$includeExtensions = @(
    '.py', '.ps1', '.psm1', '.psd1', '.sh', '.bat', '.cmd',
    '.java', '.kt', '.ts', '.tsx', '.js', '.jsx',
    '.c', '.h', '.cpp', '.hpp', '.cs', '.smali',
    '.json', '.jsonl', '.yml', '.yaml', '.toml', '.ini', '.cfg',
    '.properties', '.xml', '.gradle', '.sql',
    '.html', '.css', '.scss', '.md', '.txt'
)
$specialNames = @('Dockerfile', 'Makefile', '.gitignore', '.gitattributes', '.editorconfig')

function Get-CodeLanguage {
    param([string]$Name, [string]$Extension)
    if ($Name -eq 'Dockerfile') { return 'dockerfile' }
    if ($Name -eq 'Makefile') { return 'makefile' }
    switch ($Extension.ToLowerInvariant()) {
        '.py' { return 'python' }
        '.ps1' { return 'powershell' }
        '.psm1' { return 'powershell' }
        '.psd1' { return 'powershell' }
        '.sh' { return 'bash' }
        '.bat' { return 'batch' }
        '.cmd' { return 'batch' }
        '.java' { return 'java' }
        '.kt' { return 'kotlin' }
        '.ts' { return 'typescript' }
        '.tsx' { return 'tsx' }
        '.js' { return 'javascript' }
        '.jsx' { return 'jsx' }
        '.c' { return 'c' }
        '.h' { return 'c' }
        '.cpp' { return 'cpp' }
        '.hpp' { return 'cpp' }
        '.cs' { return 'csharp' }
        '.smali' { return 'smali' }
        '.json' { return 'json' }
        '.jsonl' { return 'jsonl' }
        '.yml' { return 'yaml' }
        '.yaml' { return 'yaml' }
        '.toml' { return 'toml' }
        '.ini' { return 'ini' }
        '.cfg' { return 'ini' }
        '.properties' { return 'properties' }
        '.xml' { return 'xml' }
        '.gradle' { return 'groovy' }
        '.sql' { return 'sql' }
        '.html' { return 'html' }
        '.css' { return 'css' }
        '.scss' { return 'scss' }
        '.md' { return 'markdown' }
        default { return 'text' }
    }
}

function Read-SourceText {
    param([string]$Path, [switch]$PermitCp949)

    $bytes = [System.IO.File]::ReadAllBytes($Path)

    # Android AXML (compiled resource XML) signature:
    # RES_XML_TYPE 0x0003, ResChunk_header.headerSize 0x0008.
    # Detect this before any text decoder (and before opt-in CP949 fallback).
    if ([System.IO.Path]::GetExtension($Path) -ieq '.xml' -and
        $bytes.Length -ge 4 -and
        $bytes[0] -eq 0x03 -and $bytes[1] -eq 0x00 -and
        $bytes[2] -eq 0x08 -and $bytes[3] -eq 0x00) {
        throw 'ANDROID_COMPILED_XML'
    }

    $text = ''
    $encodingName = 'utf-8'

    if ($bytes.Length -ge 2 -and $bytes[0] -eq 0xFF -and $bytes[1] -eq 0xFE) {
        $text = $utf16LeStrict.GetString($bytes, 2, $bytes.Length - 2)
        $encodingName = 'utf-16le-bom'
    } elseif ($bytes.Length -ge 2 -and $bytes[0] -eq 0xFE -and $bytes[1] -eq 0xFF) {
        $text = $utf16BeStrict.GetString($bytes, 2, $bytes.Length - 2)
        $encodingName = 'utf-16be-bom'
    } else {
        try {
            $text = $utf8Strict.GetString($bytes)
        } catch [System.Text.DecoderFallbackException] {
            if (-not $PermitCp949) {
                throw 'INVALID_UTF8 (use -AllowCp949 only for known CP949 files)'
            }
            # This is opt-in: arbitrary binary data must not be guessed as CP949.
            $strictCp949 = [System.Text.Encoding]::GetEncoding(
                949,
                [System.Text.EncoderFallback]::ExceptionFallback,
                [System.Text.DecoderFallback]::ExceptionFallback
            )
            $text = $strictCp949.GetString($bytes)
            $encodingName = 'cp949-opt-in'
        }
    }

    if ($text.Length -gt 0 -and $text[0] -eq [char]0xFEFF) {
        $text = $text.Substring(1)
        if ($encodingName -eq 'utf-8') { $encodingName = 'utf-8-bom' }
    }
    # Android compiled XML and other binary resources may be named .xml/.txt.
    # Reject NUL/control characters even if their bytes form valid UTF-8.
    if ([regex]::IsMatch($text, '[\x00-\x08\x0B\x0C\x0E-\x1F]')) {
        throw 'BINARY_OR_CONTROL_CHARACTERS'
    }
    return [pscustomobject]@{ Content = $text; SourceEncoding = $encodingName }
}

$included = New-Object System.Collections.ArrayList
$skipped = New-Object System.Collections.ArrayList
$pending = New-Object System.Collections.Stack
$files = New-Object System.Collections.ArrayList
$pending.Push($projectDir)

# Traverse top-down so excluded directories are never recursively scanned.
while ($pending.Count -gt 0) {
    $directory = [string]$pending.Pop()
    foreach ($entry in (Get-ChildItem -LiteralPath $directory -Force -ErrorAction Stop)) {
        $relative = $entry.FullName.Substring($projectDir.Length).TrimStart('\', '/').Replace('\', '/')
        if (($entry.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            [void]$skipped.Add([pscustomobject]@{ Path = $relative; Reason = 'REPARSE_POINT' })
            continue
        }
        if ($entry.PSIsContainer) {
            if ($excludeDirs -contains $entry.Name -or $relative -ieq 'tools/jadx') {
                continue
            }
            $pending.Push($entry.FullName)
            continue
        }
        if ($entry.FullName -ieq $outputFullPath -or $entry.FullName -ieq $reportPath) { continue }
        if ($entry.Name -match '^project(?:-source-[^.]*)?(?:\([^)]*\))?\.md$' -or
            $entry.Name -match '\.report\.json$') { continue }
        if ($entry.Name -match '^(\.env(?:\..*)?|.*\.(?:pem|p12|pfx|jks|keystore|key)|(?:secrets?|credentials?)(?:\..*)?)$') {
            [void]$skipped.Add([pscustomobject]@{ Path = $relative; Reason = 'SENSITIVE_FILENAME' })
            continue
        }
        if ($includeExtensions -notcontains $entry.Extension.ToLowerInvariant() -and
            $specialNames -notcontains $entry.Name) { continue }
        if ($entry.Length -gt ([int64]$MaxFileSizeMB * 1MB)) {
            [void]$skipped.Add([pscustomobject]@{ Path = $relative; Reason = 'TOO_LARGE' })
            continue
        }
        [void]$files.Add($entry)
    }
}

$sortedFiles = @($files | Sort-Object FullName)
$writer = [System.IO.StreamWriter]::new($outputFullPath, $false, $utf8NoBom)
$writer.NewLine = "`n"
try {
    $writer.WriteLine('# Project Source Code')
    $writer.WriteLine('')
    $writer.WriteLine('> UTF-8 text sources only. Binary resources, generated outputs and sensitive filenames are excluded.')
    $writer.WriteLine('')
    $writer.WriteLine("- Generated: $((Get-Date).ToString('yyyy-MM-dd HH:mm:ss zzz'))")
    $writer.WriteLine("- Project Path: $projectDir")
    $writer.WriteLine('')
    $writer.WriteLine('---')

    foreach ($file in $sortedFiles) {
        $relative = $file.FullName.Substring($projectDir.Length).TrimStart('\', '/').Replace('\', '/')
        try {
            $source = Read-SourceText -Path $file.FullName -PermitCp949:$AllowCp949
        } catch {
            [void]$skipped.Add([pscustomobject]@{ Path = $relative; Reason = [string]$_.Exception.Message })
            continue
        }
        if ($file.Extension -ieq '.md' -and
            $source.Content.StartsWith('# Project Source Code')) {
            [void]$skipped.Add([pscustomobject]@{ Path = $relative; Reason = 'PREVIOUS_GENERATED_SNAPSHOT' })
            continue
        }

        $language = Get-CodeLanguage -Name $file.Name -Extension $file.Extension
        $maxBackticks = 2
        foreach ($match in [regex]::Matches($source.Content, '`{3,}')) {
            $maxBackticks = [Math]::Max($maxBackticks, $match.Length)
        }
        $fence = ('`' * [Math]::Max(3, $maxBackticks + 1))

        $writer.WriteLine('')
        $writer.WriteLine("### $relative")
        $writer.WriteLine('')
        $writer.WriteLine($fence + $language)
        $writer.Write($source.Content)   # Original whitespace and line endings are preserved.
        if (-not $source.Content.EndsWith("`n")) { $writer.WriteLine() }
        $writer.WriteLine($fence)
        $writer.WriteLine('')
        $writer.WriteLine('---')
        [void]$included.Add([pscustomobject]@{
            Path = $relative
            SourceEncoding = $source.SourceEncoding
            SHA256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        })
    }
} finally {
    $writer.Dispose()
}

$report = [ordered]@{
    Phase = 'PROJECT_MD_V5_1'
    ProjectPath = $projectDir
    OutputPath = $outputFullPath
    Options = [ordered]@{
        IncludeApkText = [bool]$IncludeApkText
        IncludeArtifacts = [bool]$IncludeArtifacts
        AllowCp949 = [bool]$AllowCp949
        MaxFileSizeMB = $MaxFileSizeMB
    }
    IncludedCount = $included.Count
    SkippedCount = $skipped.Count
    Included = @($included)
    Skipped = @($skipped)
}
[System.IO.File]::WriteAllText(
    $reportPath,
    (ConvertTo-Json -InputObject $report -Depth 6),
    $utf8NoBom
)
Write-Host "SOURCE_MD = $outputFullPath"
Write-Host "REPORT    = $reportPath"
Write-Host "INCLUDED  = $($included.Count)"
Write-Host "SKIPPED   = $($skipped.Count) (see report)"
if ($skipped.Count -gt 0) {
    Write-Warning 'Some candidate source files were skipped. Review the JSON report before treating this snapshot as complete.'
}
