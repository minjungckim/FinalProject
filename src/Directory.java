public class Directory {
   private static int maxChars = 30; // max characters of each file name

   // Directory entries
   private int fsize[];        // each element stores a different file size.
   private char fnames[][];    // each element stores a different file name.

   public Directory( int maxInumber ) { // directory constructor
      fsize = new int[maxInumber];     // maxInumber = max files
      for ( int i = 0; i < maxInumber; i++ ) 
         fsize[i] = 0;                 // all file size initialized to 0
      fnames = new char[maxInumber][maxChars];
      String root = "/";                // entry(inode) 0 is "/"
      fsize[0] = root.length();        // fsize[0] is the size of "/", 2bytes.
      root.getChars( 0, fsize[0], fnames[0], 0 ); // fnames[0] includes "/"
   }

   public int bytes2directory( byte data[] ) {
      // assumes data[] received directory information from disk
      // initializes the Directory instance with this data[]
      int offset = 0;
      // Save file size data
      for (int i = 0; i < this.fsize.length; ++i, offset += 4)
         fsize[i] = SysLib.bytes2int(data, offset);

      // Save file name data
      for (int i = 0; i < this.fnames.length; ++i, offset += maxChars * 2) {
         String curFName = new String(data, offset, maxChars * 2);
         curFName.getChars(0, this.fsize[i], this.fnames[i], 0);
      }
      // return number of bytes used
      return offset;
   }

   public byte[] directory2bytes( ) {
      // converts and return Directory information into a plain byte array
      // this byte array will be written back to disk
      // note: only meaningfull directory information should be converted
      // into bytes.
      int offset = 0;

      // size of directory is 2 bytes for each file name char and 4 for each size
      byte [] data = new byte[((maxChars * 2) + 4) * fsize.length];

      // write fsize first
      for (int i = 0; i < this.fsize.length; ++i, offset += 4)
         SysLib.int2bytes(this.fsize[i], data, offset);

      // write fnames next, each taking up maxchars * 2 bytes
      for(int i = 0; i < this.fnames.length; ++i, offset += maxChars * 2) {
         String cur = new String(this.fnames[i], 0, fsize[i]);
         byte [] temp = cur.getBytes();

         // write each byte from name to data buffer
         for(int j = offset; j < temp.length; ++j) {
            data[j] = temp[j];
         }
      }
      return data;
   }

   public short ialloc( String filename ) {
      // filename is the one of a file to be created.
      // allocates a new inode number for this filename
      short i = 0;
      while(i < this.fsize.length) {
         // find the first slot with no file
         if(this.fsize[i] <= 0) {
            // save the filename length or use maxChars, whichever is smaller
            int ins;
            if(filename.length() < maxChars)
               ins = filename.length();
            else 
               ins = maxChars;
            this.fsize[i] = ins;
            
            // save the filename and return it's Inode number
            filename.getChars(0, this.fsize[i], this.fnames[i], 0);
            return i;
         }
         ++i;
      }
      return -1; // Return error
   }

   public boolean ifree( short iNumber ) {
      // deallocates this inumber (inode number)
      // the corresponding file will be deleted.
      if(this.fsize[iNumber] > 0) { 
         this.fsize[iNumber] = 0; // Found it, delete and return true;
         return true;
      }
      return false;
   }

   public short namei( String filename ) {
      // returns the inumber corresponding to this filename
      short i = 0;
      while(i < this.fsize.length) {
         String curFName = new String(this.fnames[i], 0, this.fsize[i]);
         if(filename.length() == this.fsize[i] && filename.equals(curFName))
            return i;
         i++; 
      }
      return -1; // Error finding the iNumber corresponding to fName
   }
}