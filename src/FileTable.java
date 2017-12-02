import java.util.*;

// public enum FLAG {
//    UNUSED(0), USED(1), READ(2), WRITE(3);
   
//    private final int flagNum;

//    public FLAG (int flagNum) {
//       this.flagNum = flagNum;
//    }
// }

public class FileTable {
   private final int UNUSED = 0, USED = 1, READ = 2, WRITE = 3;
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
      short inum = -1;
      Inode inode;

      for(;;) {
         if (filename.equals("/"))
            inum = 0;
         else
            inum = this.dir.namei(filename);

         if(inum == -1 && mode.equals("r")) { // Wasn't found but wanted it to be read
            return null;
         }
         else if(inum >= 0) { // Found it
            if(mode.equals("r")) {
               if(inode.flag != WRITE) {
                  inode.flag = READ;
                  break;
               }
               
               try { // If the flag is set to write, wait until it's done
                  wait();
               }
               catch (InterruptedException ex) {
               }
            }
            else {
               if(inode.flag == USED || inode.flag == UNUSED) {
                  inode.flag = WRITE;
                  break;
               }

               try {
                  wait();
               }
               catch(InterruptedException ex) {
               }
            }
         } 
         else {
            inum = dir.ialloc(filename);
            inode = new Inode(inum);
            inode.flag = WRITE;
            break;
         }
      }

      inode.count++;
      inode.toDisk(inum);
      FileTableEntry newEntry = new FileTableEntry(inode, inum, mode);
      table.addElement(newEntry);
      return newEntry;
   }

   public synchronized boolean ffree( FileTableEntry e ) {
      // receive a file table entry reference
      // save the corresponding inode to the disk
      // free this file table entry.
      // return true if this file table entry found in my table
      Inode curInode = new Inode(e.iNumber);

      if (this.table.remove(e))
        {   
            if (curInode.flag == READ && curInode.count == 1)
            {
               notify();
               curInode.flag = USED;
            }
            if (curInode.flag == WRITE)
            {
                e.inode.flag = USED;
                notifyAll();
            }
            curInode.count--;
            curInode.toDisk(e.iNumber);
            return true;
        }
        else return false;
   }

   public synchronized boolean fempty( ) {
      return table.isEmpty( );  // return if table is empty 
   }                            // should be called before starting a format
}