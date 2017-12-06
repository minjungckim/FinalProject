/*
 ** Authors: Bobby Damore, Connie Kim
 ** CSS 430
 ** Superblock.java
 */

class Superblock {
	private final static int defaultTotalInodes = 64;

	public int totalBlocks; // the number of disk blocks
	public int totalInodes; // the number of inodes
	public int freeList;    // the block number of the free list's head

	/* 
	 ** Constructor
	 */
	public Superblock( int diskSize ) {
		byte[] superblock = new byte[Disk.blockSize];

		SysLib.rawread(0, superblock);
		totalBlocks = SysLib.bytes2int(superblock, 0);
		totalInodes = SysLib.bytes2int(superblock, 4);
		freeList = SysLib.bytes2int(superblock, 8);

		// check that superblock contains expected data
		if (totalBlocks == diskSize && totalInodes > 0 && freeList >= 2)
			return;

		// otherwise, format file system
		totalBlocks = diskSize;
		format(defaultTotalInodes);
	}

	/* 
	 ** Format method allows data to be cleared and restructures to the original format. 
	 ** Superblock's data members will be cleared and will be updated accordingly by calling
	 ** the synch() method.
	 */
	public void format(int inodes) {

		// initialize inodes number of new Inodes
		totalInodes = inodes;
		for(short i = 0; i < totalInodes; ++i) {
			Inode ins = new Inode();
			ins.toDisk(i);
		}

		// freeList head points to first block after Inodes
		freeList = ((totalInodes - 1) / (Disk.blockSize / Inode.iNodeSize)) + 2;

		// write pointer to next free block for each free block
		byte [] block = new byte[Disk.blockSize];
		for(int i = freeList; i < 1000; ++i) {
			SysLib.int2bytes(i + 1, block, 0);
			SysLib.rawwrite(i, block);
		}

		// write -1 to last block to show end of freeList
		SysLib.int2bytes(-1, block, 0);
		SysLib.rawwrite(1000 - 1, block);

		// synch superblock data back to disk
		synch();
	}

	/* 
	 ** Synch will be used by format method to update
	 ** information of the Superblock back to Disk.
	 */
	public void synch () {
		// write superblock contents to buffer, then to disk
		byte [] superblock = new byte[Disk.blockSize];
		SysLib.int2bytes(totalBlocks, superblock, 0);
		SysLib.int2bytes(totalInodes, superblock, 4);
		SysLib.int2bytes(freeList, superblock, 8);
		SysLib.rawwrite(0, superblock);
	}

	public void addToFreeList(int block)
	{
		byte[] nextFreeBlock = new byte[Disk.blockSize];
		SysLib.int2bytes(freeList, nextFreeBlock, 0);
		SysLib.rawwrite(block, nextFreeBlock);
		freeList = block;
	}

	public int allocFromFreeList()
	{
		byte[] nextFreeBlock = new byte[Disk.blockSize];
		SysLib.rawread(freeList, nextFreeBlock);
		int nextFree = SysLib.bytes2int(nextFreeBlock, 0);
		int allocBlock = freeList;
		freeList = nextFree;
		return allocBlock;
	}
}
