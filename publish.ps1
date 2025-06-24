$activeBranch = git rev-parse --abbrev-ref HEAD

$switchToBranch = 'forge-1.20.1'

if($activeBranch -eq "forge-1.20.1") {
    $switchToBranch = 'neoforge-1.21.1'
}

$ErrorActionPreference = 'Stop'
./gradlew clean publishAll

git checkout $switchToBranch
./gradlew clean publishAll

git checkout $activeBranch