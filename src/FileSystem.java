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

		// create filetable
		filetable = new FileTable(directory);

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
		// Write the root to disk
		FileTableEntry rootEntry = open("/", "w"); 

		// Write this directory into root
		write(rootEntry, directory.directory2bytes());
		close(rootEntry);

		// Lastly call superblock to write to disk
		superblock.synch();
	}

	boolean format(int files)
	{
		superblock.format(files);
		directory = new Directory(superblock.totalInodes);
		filetable = new FileTable(directory);

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

		return true;
	}

	FileTableEntry open(String filename, String mode)
	{
		return filetable.falloc(filename, mode);
	}

	synchronized boolean close(FileTableEntry ftEnt)
	{
		return filetable.ffree(ftEnt);
	}

	synchronized int fsize(FileTableEntry ftEnt)
	{
		return ftEnt.inode.length;
	}

	synchronized int read(FileTableEntry ftEnt, byte[] buffer)
	{
		if(!ftEnt.mode.equals("r") && !ftEnt.mode.equals("w+")) return -1;

		Inode entNode = ftEnt.inode;
		// If inode is currently being used, wait to finish
		while(entNode.flag != Inode.UNUSED) {
			try { 
				wait();
			}
			catch (InterruptedException ex) {
			}
		}

		// Set Inode flag to read
		entNode.flag = Inode.READ;

		byte[] tempBuffer = new byte[Disk.blockSize];
		int bytesRead = 0;

		// Calculate which direct or idirect ref to start, then offset within
		int blockOffset = ftEnt.seekPtr / Disk.blockSize;
		int innerOffset = ftEnt.seekPtr % Disk.blockSize;

		// Each iteration reads one block from disk, and then to the buffer
		while(ftEnt.seekPtr < entNode.length)
		{
			// Figure out the current block using seek Ptr, and read into memory
			short currentBlock = -1;
			if(blockOffset < entNode.direct.length) 
				currentBlock = entNode.direct[blockOffset]; 
			else
			{
				SysLib.rawread(entNode.indirect, tempBuffer);
				currentBlock = SysLib.bytes2short(tempBuffer, (blockOffset - entNode.direct.length) * 2);
			}
			if(currentBlock == -1) break;
			SysLib.rawread(currentBlock, tempBuffer);

			// copy each byte until reaching EOF, end of buffer, or end of tempBuffer
			for(int i = innerOffset; i < tempBuffer.length && ftEnt.seekPtr < entNode.length && bytesRead < buffer.length; ++i, ++ftEnt.seekPtr)
			{
				buffer[bytesRead++] = tempBuffer[i];
			}

			blockOffset++;	// Increment to next block
			innerOffset = 0;	// Reset offset since we are at beginning of new block
		}
		// Set flag back to unused and notify anything waiting
		entNode.flag = Inode.UNUSED;
		notify();

		return bytesRead;
	}

	synchronized int write(FileTableEntry ftEnt, byte[] buffer)
	{
		if(ftEnt.mode.equals("r")) return -1;

		Inode entNode = ftEnt.inode;
		// If inode is currently being used, wait to finish
		while(entNode.flag != Inode.UNUSED) {
			try { 
				wait();
			}
			catch (InterruptedException ex) {
			}
		}

		// Set Inode flag to write
		entNode.flag = Inode.WRITE;

		byte[] tempBuffer = new byte[Disk.blockSize];
		int bytesWritten = 0;

		// Calculate which direct or idirect ref to start, then offset within
		int blockOffset = ftEnt.seekPtr / Disk.blockSize;
		int innerOffset = ftEnt.seekPtr % Disk.blockSize;

		// Each iteration writes to one block using buffer
		while(bytesWritten < buffer.length)
		{
			// Figure out the current block using seek Ptr, and read into memory
			// If seek goes past EOF, alloc another block for the file
			short currentBlock = -1;
			if(blockOffset < entNode.direct.length) {
				currentBlock = entNode.direct[blockOffset]; 
				if(currentBlock == -1)
				{
					byte[] blockAsBytes = new byte[4];
					SysLib.int2bytes(superblock.allocFromFreeList(), blockAsBytes, 0);
					currentBlock = SysLib.bytes2short(blockAsBytes, 2);
					entNode.direct[blockOffset] = currentBlock;
				}
			}
			else
			{
				// Make sure there is an indirect block to read from
				if(entNode.indirect == -1)
				{
					byte[] blockAsBytes = new byte[4];
					SysLib.int2bytes(superblock.allocFromFreeList(), blockAsBytes, 0);
					entNode.allocIndirectBlock(SysLib.bytes2short(blockAsBytes, 2));
				}

				// Read indirect block in from Disk
				SysLib.rawread(entNode.indirect, tempBuffer);
				currentBlock = SysLib.bytes2short(tempBuffer, (blockOffset - entNode.direct.length) * 2);

				// Allocate another block for indirect to point to if -1
				if(currentBlock == -1)
				{	
					byte[] blockAsBytes = new byte[4];
					SysLib.int2bytes(superblock.allocFromFreeList(), blockAsBytes, 0);
					currentBlock = SysLib.bytes2short(blockAsBytes, 2);
					SysLib.short2bytes(currentBlock, tempBuffer, (blockOffset - entNode.direct.length) * 2);
					SysLib.rawwrite(entNode.indirect, tempBuffer);
				}
			}
			SysLib.rawread(currentBlock, tempBuffer);

			// copy each byte until reaching EOF, end of buffer, or end of tempBuffer
			for(int i = innerOffset; i < tempBuffer.length && bytesWritten < buffer.length; ++i, ++ftEnt.seekPtr)
			{
				tempBuffer[i] = buffer[bytesWritten++];
			}

			// Write block back to disk when done with it
			SysLib.rawwrite(currentBlock, tempBuffer);

			blockOffset++;	// Increment to next block
			innerOffset = 0;	// Reset offset since we are at beginning of new block
		}
		// Increase file size if we wrote past EOF
		if(ftEnt.seekPtr > entNode.length) entNode.length = ftEnt.seekPtr;
		ftEnt.inode.toDisk(ftEnt.iNumber); // Need to write back to disk
 
		// Set flag back to unused and notify anything waiting
		entNode.flag = Inode.UNUSED;
		notify();

		return bytesWritten;
	}

	boolean delete(String filename)
	{
		// Get the file we want to delete 
		short toDelete = directory.namei(filename);
		FileTableEntry ftEnt = open(filename, "r"); // open the file with the filename
		
		// If we were able to close and deallocates the inumber and its corresponding file,
		// it should return true
		return (close(ftEnt) && directory.ifree(toDelete));
	}

	private final int SEEK_SET = 0;
	private final int SEEK_CUR = 1;
	private final int SEEK_END = 2;

	synchronized int seek(FileTableEntry ftEnt, int offset, int whence)
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
			break;
		default: 
			return -1;
		}
		// Clamp seekPtr to front or end of file
		if(ftEnt.seekPtr < 0)
			ftEnt.seekPtr = 0;
		else if(ftEnt.seekPtr > ftEnt.inode.length)
			ftEnt.seekPtr = ftEnt.inode.length;

		return ftEnt.seekPtr;
	}
}
