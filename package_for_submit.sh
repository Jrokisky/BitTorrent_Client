mkdir Group12pa2
cp -R doc Group12pa2
cp -R edu Group12pa2
cp src/*.java Group12pa2
cp writeup.pdf Group12pa2
echo "Enter NetID: "
read NETID
tar czf $NETID.tar.gz Group12pa2
rm -rf Group12pa1
echo "Wrote to $NETID.tar.gz"
