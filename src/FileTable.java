import java.util.*;

public class FileTable {
   private final static int UNUSED = 0, USED = 1, READ = 2, WRITE = 3, WRITEP = 4, APPEND = 5;
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
      Inode inode = null;
      int seekPtr = 0;

      for(;;) {
         // lookup filename in directory to get inum
         if (filename.equals("/"))
            inum = 0;
         else
            inum = this.dir.namei(filename);

         if(inum >= 0) { // Found it
            inode = new Inode(inum);
            // If the flag is set to write, wait until it's done
            while(inode.flag > READ) {
                  try { 
                     wait();
                 }
                 catch (InterruptedException ex) {
                 }
            }
            // Set flag based on given mode
            // Reading okay while not writing
            if(mode.equals("r")) {
               
               inode.flag = READ;

               break;
            }
            // Writing should wait for reading to finish also
            else {
               // wait for Inode to be unused
               while(inode.flag != UNUSED) {
                  try { 
                      wait();
                 }
                 catch (InterruptedException ex) {
                 }
               }

               if(mode.equals("w")) {
               inode.flag = WRITE;
               break;
               }
               else if(mode.equals("w+")) {
                  inode.flag = WRITEP;
                  break;
               }
               else if(mode.equals("a")) {
                  inode.flag = APPEND;
                  seekPtr = inode.length;
                  break;
               }
               else {
                 return null;
               }
            } 
         } 
         else if(inum < 0 && (mode.equals("w") || mode.equals("a") || mode.equals("w+"))) { // Wasn't found, want to write
            // allocate new file in the directory, and use inum to allocate new Inode
            inum = dir.ialloc(filename);
            inode = new Inode(inum);

            if(mode.equals("w"))
               inode.flag = WRITE;
            else if(mode.equals("w+"))
               inode.flag = WRITEP;
            else  
               inode.flag = APPEND;

            break;
         } 
         else return null;
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
      Inode curInode = new Inode(e.iNumber);

      if (this.table.remove(e))
      {   
         // decrement Inode count and set to UNUSED if no one using
         curInode.count--;
         if(curInode.count <= 0)
            curInode.flag = UNUSED;

         // write Inode to disk and notify everything waiting
         curInode.toDisk(e.iNumber);
         notifyAll();

         return true;
       }
       else return false;
   }

   public synchronized boolean fempty( ) {
      return table.isEmpty( );  // return if table is empty 
   }                            // should be called before starting a format
}