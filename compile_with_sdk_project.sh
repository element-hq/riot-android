echo replace step 1
sed -i '' -e "s/^\/\/include ':matrix-sdk'/include ':matrix-sdk'/" settings.gradle || true
sed -i '' -e "s/^\/\/project(':matrix-sdk')/project(':matrix-sdk')/" settings.gradle || true

echo replace step 2
sed -i '' -e "s/^    implementation(name: 'matrix/    \/\/implementation(name: 'matrix/" vector/build.gradle || true
sed -i '' -e "s/^    \/\/implementation project(':matrix-/    implementation project(':matrix-/" vector/build.gradle || true

echo "please sync project with gradle files"
