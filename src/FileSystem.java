public class FileSystem {
	private Superblock superblock;
	private Directory directory;
	private FileTable filetable;

	public FileSystem(int diskBlocks)
	{
		// create superblock, and format disk with 64 inodes in default
		superblock = new Superblock(diskBlocks);

		// create directory, and register "/" in directory entry 0
		directory = new Directory(superblock.totalInodes);

		// directory reconstruction
		FileTableEntry dirEnt = open("/", "r");
		int dirSize = fsize(dirEnt);
		if(dirSize > 0)
		{
			byte[] dirData = new byte[dirSize];
			read(dirEnt, dirData);
			directory.bytes2directory(dirData);
		}
		close(dirEnt);
	}

	void sync()
	{
		superblock.synch();
	}

	boolean format(int files)
	{
		superblock.format(files);
		return true;
	}

	FileTableEntry open(String filename, String mode)
	{
		return filetable.falloc(filename, mode);
	}

	boolean close(FileTableEntry ftEnt)
	{
		return filetable.ffree(ftEnt);
	}

	int fsize(FileTableEntry ftEnt)
	{
		return ftEnt.inode.length;
	}

	int read(FileTableEntry ftEnt, byte[] buffer)
	{
		Inode entNode = ftEnt.inode;
		byte[] tempBuffer = new byte[Disk.blockSize];
		int bytesRead = 0;

		// Calculate which direct or idirect ref to start, then offset within
		int blockOffset = ftEnt.seekPtr / Disk.blockSize;
		int innerOffset = ftEnt.seekPtr % Disk.blockSize;

		// Each iteration reads one block from disk, and then to the buffer
		while(ftEnt.seekPtr < entNode.length)
		{
			// Figure out the current block using seek Ptr, and read into memory
			int currentBlock = -1;
			if(blockOffset < entNode.direct.length) 
				currentBlock = entNode.direct[blockOffset]; 
			else
			{
				SysLib.rawread(entNode.indirect, tempBuffer);
				currentBlock = SysLib.bytes2int(tempBuffer, (blockOffset - entNode.direct.length) * 4)
			}
			if(currentBlock == -1) break;
			SysLib.rawread(currentBlock, tempBuffer);
			
			// copy each byte until reaching EOF, end of buffer, or end of tempBuffer
			for(int i = innerOffset; i < tempBuffer.length && ftEnt.seekPtr < entNode.length && bytesRead < buffer.length; ++i)
			{
				buffer[bytesRead++] = tempBuffer[i];
			}
			innerOffset = 0;
			blockOffset++;
		}
		ftEnt.seekPtr += bytesRead;
		return bytesRead;
	}

	int write(FileTableEntry ftEnt, byte[] buffer)
	{
		
	}

	private boolean deallocAllBlocks(FileTableEntry ftEnt)
	{
		Inode inode = ftEnt.inode;
		boolean reachedEnd = false;

		// dealloc direct blocks
		for(int i = 0; i < inode.direct.length && !reachedEnd; ++i)
		{
			int block = inode.direct[i];
			if(block == -1)
				reachedEnd = true;
			else
				superblock.addToFreeList(block);
		}

		// dealloc indirect blocks
		if(!reachedEnd)
		{
			byte[] indirectBuffer = new byte[Disk.blockSize];
			SysLib.rawread(inode.indirect, indirectBuffer);
			for(int i = 0; i < Disk.blockSize && !reachedEnd; i += 4)
			{
				int block = SysLib.bytes2int(indirectBuffer, i);
				if(block == -1)
					reachedEnd = true;
				else
					superblock.addToFreeList(block);
			}
		}

		inode = new Inode();
		if(inode.toDisk(ftEnt.iNumber) != -1)
			return true;
		else
			return false;
	}

	boolean delete(String filename)
	{
		// Remove file from directory
		short inum = directory.namei(filename);
		if(!directory.ifree(inum))
			return false;

		// Wait until file is not open
		Inode inode = new Inode(inum);
		while(inode.count > 0)
		{
			try
			{
				wait();
			}
			catch(InterruptedException ex){}
		}
		// Dealloc all file data
		return deallocAllBlocks(new FileTableEntry(inode, inum, "r"));
	}

	private final int SEEK_SET = 0;
	private final int SEEK_CUR = 1;
	private final int SEEK_END = 2;

	int seek(FileTableEntry ftEnt, int offset, int whence)
	{
		switch(whence)
		{
			case SEEK_SET:
				ftEnt.seekPtr = offset;
				break;
			case SEEK_CUR:
				ftEnt.seekPtr += offset;
				break;
			case SEEK_END:
				ftEnt.seekPtr = ftEnt.inode.length + offset;
		}
		// Clamp seekPtr to front or end of file
		if(ftEnt.seekPtr < 0)
			ftEnt.seekPtr = 0;
		else if(ftEnt.seekPtr > ftEnt.inode.length)
			ftEnt.seekPtr = ftEnt.inode.length;
		return 0;
	}
}