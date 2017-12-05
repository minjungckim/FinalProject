import java.util.*;

public class FileTable {
	private Vector<FileTableEntry> table;         // the actual entity of this file table
	private Directory dir;        // the root directory 

	public FileTable( Directory directory ) { // constructor
		table = new Vector<FileTableEntry>();     // instantiate a file (structure) table
		dir = directory;           // receive a reference to the Director
	}                             // from the file system

	// major public methods
	public synchronized FileTableEntry falloc( String filename, String mode ) {
		// allocate a new file (structure) table entry for this file name
		// allocate/retrieve and register the corresponding inode using dir
		// increment this inode's count
		// immediately write back this inode to the disk
		// return a reference to this file (structure) table entry
		if(!mode.equals("r") && !mode.equals("w") && !mode.equals("w+") && !mode.equals("a")) return null;

		short inum = -1;
		Inode inode;
		int seekPtr = 0;

		// lookup filename in directory to get inum
		if (filename.equals("/"))
			inum = 0;
		else
			inum = this.dir.namei(filename);

		// Wasn't found but wanted to read
		if(inum == -1 && mode.equals("r")) { 
			return null;
		}
		// Wasn't found, want to write
		else if(inum == -1){ 
			// allocate new file in the directory, create a new Inode
			inum = dir.ialloc(filename);
			inode = new Inode();
		}
		// Found it
		else { 
			// Get Inode from Disk
			inode = new Inode(inum);
			
			// Set seekPtr to end if in append mode
			if(mode.equals("a"))
				seekPtr = inode.length;
		} 

		// Increment Inode count, write back to disk
		inode.count++;
		inode.toDisk(inum);

		// Allocate new FileTableEntry in the FileTable and return a ref to it
		FileTableEntry newEntry = new FileTableEntry(inode, inum, mode);
		newEntry.seekPtr = seekPtr;
		table.addElement(newEntry);
		return newEntry;
	}

	public synchronized boolean ffree( FileTableEntry e ) {
		// receive a file table entry reference
		// save the corresponding inode to the disk
		// free this file table entry.
		// return true if this file table entry found in my table

		if (this.table.remove(e))
		{   
			Inode curInode = new Inode(e.iNumber);

			// decrement Inode count and set to UNUSED if no one using
			curInode.count--;
			if(curInode.count <= 0)
				curInode.flag = Inode.UNUSED;

			// write Inode to disk
			curInode.toDisk(e.iNumber);

			return true;
		}
		else return false;
	}

	public synchronized boolean fempty( ) {
		return table.isEmpty( );  // return if table is empty 
	}                            // should be called before starting a format
}
