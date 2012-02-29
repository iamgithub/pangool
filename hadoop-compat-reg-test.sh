#!/bin/bash

PANGOLINDIR=`pwd`
WORKDIR=$PANGOLINDIR/hadoop-compat-regtest
POMSDIR=$PANGOLINDIR/core/poms
POM=$PANGOLINDIR/core/pom.xml
if [ -d $POMSDIR ] ; then
	rm -r $POMSDIR
fi
mkdir $POMSDIR

CURRENT_VERSION="0.20.203.0"

LINE_NUMBER=`grep -n -A 1 "hadoop-core" $POM | cut -d '-' -f1 | tail -1`
echo "Found hadoop-core version in $POM in line: $LINE_NUMBER"
echo "Generating poms..."
sed -e "${LINE_NUMBER}s/${CURRENT_VERSION}/0.20.2-cdh3u3/" $POM > $POMSDIR/pom-0.20.2-cdh3u3.xml

#sed -e "${LINE_NUMBER}s/${CURRENT_VERSION}/1.0.0/" pom.xml > poms/pom-1.0.0.xml
#sed -e "${LINE_NUMBER}s/${CURRENT_VERSION}/0.20.204.0/" pom.xml > poms/pom-0.20.204.0.xml
#sed -e "${LINE_NUMBER}s/${CURRENT_VERSION}/0.20.205.0/" pom.xml > poms/pom-0.20.205.0.xml

#sed -e "${LINE_NUMBER}s/${CURRENT_VERSION}/0.20.2-737/" pom.xml > poms/pom-0.20.2-737.xml
echo "Done. Poms generated in $POMSDIR"

echo "Work dir: $WORKDIR"

if [ ! -d $WORKDIR ] ; then
	echo "Creating folder for regtest: $WORKDIR"
	mkdir $WORKDIR
fi

cd $POMSDIR
for file in *.xml ; do
	echo "$file"
	CURRENTDIR=$WORKDIR/$file
	if [ -d $CURRENTDIR ] ; then
		echo "Cleaning existing dir $CURRENTDIR"
		rm -rf $CURRENTDIR
	fi
	mkdir $CURRENTDIR
	git clone 'git@github.com:datasalt/pangool.git' $CURRENTDIR
	rm $CURRENTDIR/core/pom.xml
	cp $file $CURRENTDIR/core/pom.xml
	
	cd $CURRENTDIR
	echo "Building for version: $file ..."
	MVNOUTPUT=`mvn clean package`
	echo $MVNOUTPUT
	if [[ "$MVNOUTPUT" == *ERROR* ]]
	then
		echo "Build failed for version: $file"
		exit -1
	fi
	echo "Build OK for version: $file"
	cd $POMSDIR
done
