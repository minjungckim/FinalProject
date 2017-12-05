/*
 ** Authors: Bobby Damore, Connie Kim
 ** CSS 430
 ** Inode.java
 */

/*
 ** This class is a data structure used to represent a file or directory.
 ** 
 */
public class Inode {
	public final static int UNUSED = 0, READ = 1, WRITE = 2;
	public final static int iNodeSize = 32;       // fix to 32 bytes
	private final static int directSize = 11;      // # direct pointers
	private final static int totalInodes = Disk.blockSize / iNodeSize;

	public int length;                             // file size in bytes
	public short count;                            // # file-table entries pointing to this
	public short flag;                             // 0 = unused, 1 = used, 2 = read, 3 = write
	public short direct[] = new short[directSize]; // direct pointers
	public short indirect;                         // a indirect pointer

	/* 
	 ** Default Constructor
	 */
	Inode( ) {                                     // a default constructor
		length = 0;
		count = 0;
		flag = 0;
		for ( int i = 0; i < directSize; i++ )
			direct[i] = -1;
		indirect = -1;
	}

	/* 
	 ** Constructor
	 */
	Inode( short iNumber ) {                       // retrieving inode from disk
		if(iNumber >= 0) {
			int blockNum, offset;
			byte [] data = new byte[Disk.blockSize];

			// read corresponding block into memory
			blockNum = (iNumber / totalInodes) + 1;
			SysLib.rawread(blockNum, data);

			// Use offset to read iNode data from block
			offset = (iNumber % totalInodes) * iNodeSize;

			length = SysLib.bytes2int(data, offset);
			offset += 4;

			count = SysLib.bytes2short(data, offset);
			offset += 2;

			flag = SysLib.bytes2short(data, offset);
			offset += 2;

			for (int i = 0; i < directSize; ++i, offset += 2)
				direct[i] = SysLib.bytes2short(data, offset);

			indirect = SysLib.bytes2short(data, offset);
		}
		else {
			length = -1;
			count = -1;
			flag = -1;
			for (int i = 0; i < directSize; ++i)
				direct[i] = -1;
			indirect = -1;
		}
	}

	/* 
	 ** toDisk method takes in an iNumber and 
	 ** writes the information of the corresponding 
	 ** Inode back into the disk.
	 */
	int toDisk( short iNumber ) {                  // save to disk as the i-th inode
		if(iNumber >= 0) {
			int blockNum, offset;
			byte [] data = new byte[Disk.blockSize];

			// read corresponding block into memory
			blockNum = (iNumber / totalInodes) + 1;
			SysLib.rawread(blockNum, data);

			// use offset to write iNode data to block
			offset = (iNumber % totalInodes) * iNodeSize;

			SysLib.int2bytes(length, data, offset);
			offset += 4;

			SysLib.short2bytes(count, data, offset);
			offset += 2;

			SysLib.short2bytes(flag, data, offset);
			offset += 2;

			for (int i = 0; i < directSize; ++i, offset += 2)
				SysLib.short2bytes(direct[i], data, offset);

			SysLib.short2bytes(indirect, data, offset);
			offset += 2;

			// write block back to disk and return num bytes written
			SysLib.rawwrite(blockNum, data);
			return offset;
		}
		return -1;
	}

	// Use given blockNum to initialize indirect pointers
	public void allocIndirectBlock(short blockNum)
	{
		indirect = blockNum;
		byte[] buffer = new byte[Disk.blockSize];
		SysLib.rawread(blockNum, buffer);
		for(int i = 0; i < buffer.length; i += 2)
		{
			SysLib.short2bytes((short)-1, buffer, i);
		}
		SysLib.rawwrite(blockNum, buffer);
	}

	public boolean deallocAllBlocks(short iNum, Superblock superblock)
	{
		boolean reachedEnd = false;

		// dealloc direct blocks
		for(int i = 0; i < direct.length && !reachedEnd; ++i)
		{
			short block = direct[i];
			if(block == -1)
				reachedEnd = true;
			else
				superblock.addToFreeList(block);
		}

		// dealloc indirect blocks
		if(!reachedEnd && indirect != -1)
		{
			byte[] indirectBuffer = new byte[Disk.blockSize];
			SysLib.rawread(indirect, indirectBuffer);
			for(int i = 0; i < Disk.blockSize && !reachedEnd; i += 2)
			{
				short block = SysLib.bytes2short(indirectBuffer, i);
				if(block == -1)
					reachedEnd = true;
				else
					superblock.addToFreeList(block);
			}
		}

		// Write new empty Inode at iNum
		Inode inode = new Inode();
		if(inode.toDisk(iNum) != -1)
			return true;
		else
			return false;
	}
}