$script_file = $MyInvocation.MyCommand.Path
$project_dir = Resolve-Path $script_file\..\..

$build_os=$args[0]
$build_arch=$args[1]

#Write-Output "script_file : $script_file"
#Write-Output "project_dir : $project_dir"
#Write-Output "build_os : $build_os"
#Write-Output "build_arch : $build_arch"

if ([string]::IsNullOrEmpty($build_os) -Or [string]::IsNullOrEmpty($build_arch)) {
    Write-Output "Usage: script [build_os] [build_arch]"
    Exit 1
}

cd $project_dir\native
Write-Output "Building native lib..."

$target = ""
if ($build_os -Eq "windows" -And $build_arch -Eq "x64") {
    $target = "x86_64-pc-windows-msvc"
} elseif ($build_os -Eq "windows" -And $build_arch -Eq "x32") {
    $target = "i686-pc-windows-msvc"
} else {
    Write-Output "This script is not setup to build $build_os-$build_arch (add it if you need it)"
    Exit 1
}

cargo build --release --target=$target

$input_dir = "$project_dir\native\target\$target\release"
$output_dir = "$project_dir\shmemj-$build_os-$build_arch\src\main\resources\jne\${build_os}\${build_arch}"
Write-Output "Copying $input_dir\*.dll -> $output_dir"

#Copy-Item "$input_dir\*.dll" -Destination "$output_dir" -verbose
Copy-Item -Path "$input_dir\*.dll" -Destination "$output_dir" -PassThru | ForEach-Object { Write-Output "Copied $_" }