/*
** Authors: Bobby Damore, Connie Kim
** CSS 430
** Superblock.java
*/

class Superblock {
   public int totalBlocks; // the number of disk blocks
   public int totalInodes; // the number of inodes
   public int freeList;    // the block number of the free list's head
   
   /* 
   ** Constructor
   */
   public SuperBlock( int diskSize ) {
		byte[] superblock = new byte[Disk.blockSize];

		SysLib.rawread(0, superblock);
		totalBlocks = SysLib.bytes2int(superblock, 0);
		totalInodes = SysLib.bytes2int(superblock, 4);
		freeList = SysLib.bytes2int(superblock, 8);

		if (totalBlocks == diskSize && totalInodes > 0 && freeList >= 2)
			return;

		totalBlocks = diskSize;
		format(64);
	}

	/* 
	** Format method allows data to be cleared and restructures to the original format. 
	** Superblock's data members will be cleared and will be updated accordingly by calling
	** the synch() method.
	*/
	public void format(int inodes) {
		totalInodes = inodes;

		for(int i = 0; i < inodes; ++i) {
			Inode ins = new Inode();
			ins.toDisk((short) i);
		}

		freeList = (totalInodes / 16) + 2;

		for(int i = freeList; i < 64 - 1; ++i) {
			byte [] block = new byte[Disk.blockSize];

			for(int j = 0; j < Disk.blockSize; ++j)
				block[j] = 0;

			SysLib.int2bytes(i + 1, block, 0);
			SysLib.rawwrite(i, block);
		}

		SysLib.int2bytes(-1, block, 0);
		SysLib.rawwrite(defaultBlocks - 1, block);

		synch();
	}

	/* 
	** Synch will be used by format method to update
	** information of the Superblock back to Disk.
	*/
	public void synch () {
		byte [] superblock = new byte[Disk.blockSize];
		SysLib.int2bytes(totalBlocks, block, 0);
		SysLib.int2bytes(totalInodes, block, 4);
		SysLib.int2bytes(freeList, block, 8);
		SysLib.rawwrite(0, block);
	}
}