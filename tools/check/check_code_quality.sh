#!/usr/bin/env bash

echo "Check drawable quantity"

numberOfFiles1=`ls -1U ./vector/src/main/res/drawable-hdpi | wc -l | sed  "s/ //g"`
numberOfFiles2=`ls -1U ./vector/src/main/res/drawable-mdpi | wc -l | sed  "s/ //g"`
numberOfFiles3=`ls -1U ./vector/src/main/res/drawable-xhdpi | wc -l | sed  "s/ //g"`
numberOfFiles4=`ls -1U ./vector/src/main/res/drawable-xxhdpi | wc -l | sed  "s/ //g"`
numberOfFiles5=`ls -1U ./vector/src/main/res/drawable-xXxhdpi | wc -l | sed  "s/ //g"`

if [ $numberOfFiles1 -eq $numberOfFiles5 ] && [ $numberOfFiles2 -eq $numberOfFiles5 ] && [ $numberOfFiles3 -eq $numberOfFiles5 ] && [ $numberOfFiles4 -eq $numberOfFiles5 ]; then
   resultNbOfDrawable=0
   echo "OK"
else
   resultNbOfDrawable=1
   echo "ERROR, missing drawable alternative."
fi

echo
echo "Search for forbidden patterns in code..."

./tools/check/search_forbidden_strings.pl ./tools/check/forbidden_strings_in_code.txt \
    ./vector/src/app/java \
    ./vector/src/appfdroid/java \
    ./vector/src/main/java

resultForbiddenStringInCode=$?

echo
echo "Search for forbidden patterns in resources..."

./tools/check/search_forbidden_strings.pl ./tools/check/forbidden_strings_in_resources.txt \
    ./vector/src/main/res/layout \
    ./vector/src/main/res/menu \
    ./vector/src/main/res/values \
    ./vector/src/main/res/values-v21 \
    ./vector/src/main/res/values-w820dp

resultForbiddenStringInResource=$?

echo
echo "Search for png files in /drawable..."

ls -1U ./vector/src/main/res/drawable/*.png
resultTmp=$?

# Inverse the result, cause no file found is an error for ls but this is what we want!
if [ $resultTmp -eq 0 ]; then
   echo "ERROR, png files detected in /drawable"
   resultPngInDrawable=1
else
   echo "OK"
   resultPngInDrawable=0
fi

echo

if [ $resultNbOfDrawable -eq 0 ] && [ $resultForbiddenStringInCode -eq 0 ] && [ $resultForbiddenStringInResource -eq 0 ] && [ $resultPngInDrawable -eq 0 ]; then
   echo "MAIN OK"
else
   echo "MAIN ERROR"
   exit 1
fi
