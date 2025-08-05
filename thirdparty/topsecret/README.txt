These files must be retrieved from your PS4 and placed here:

/app/NPXS20113/bdjstack/bdjstack.jar
/app/NPXS20113/bdjstack/lib/rt.jar

If you don't have a jailbroken PS4, there's a roundabout way to get them. You can grab a decrypted
firmware 7.00 package from https://darthsternie.net/ps4-decrypted-firmwares/ and extract them from
PS4UPDATE2.PUP.dec using ps4-pup-unpacker:

sudo apt install cmake
git clone --recurse-submodules https://github.com/Zer0xFF/ps4-pup-unpacker
cmake -Bps4-pup-unpacker -Sps4-pup-unpacker
cmake --build ps4-pup-unpacker
ps4-pup-unpacker/pup_unpacker PS4UPDATE2.PUP.dec
sudo mkdir -p /mnt/ps4 && sudo mount PS4UPDATE2/system_ex_fs_image.img /mnt/ps4
cp /mnt/ps4/app/NPXS20113/bdjstack/bdjstack.jar thirdparty/topsecret/
cp /mnt/ps4/app/NPXS20113/bdjstack/lib/rt.jar thirdparty/topsecret/
sudo umount /mnt/ps4 && sudo rmdir /mnt/ps4