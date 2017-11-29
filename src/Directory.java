public class Directory {
   private static int maxChars = 30; // max characters of each file name

   // Directory entries
   private int fsize[];        // each element stores a different file size.
   private char fnames[][];    // each element stores a different file name.
   private int totalDirSize;

   public Directory( int maxInumber ) { // directory constructor
      fsize = new int[maxInumber];     // maxInumber = max files
      for ( int i = 0; i < maxInumber; i++ ) 
         fsize[i] = 0;                 // all file size initialized to 0
      fnames = new char[maxInumber][maxChars];
      String root = "/";                // entry(inode) 0 is "/"
      fsize[0] = root.length( );        // fsize[0] is the size of "/".
      root.getChars( 0, fsize[0], fnames[0], 0 ); // fnames[0] includes "/"
   }

   public int bytes2directory( byte data[] ) {
      // assumes data[] received directory information from disk
      // initializes the Directory instance with this data[]
      int offset = 0;
      for (int i = 0; i < this.fsize.length; ++i, offset += 4)
         fsize[i] = SysLib.bytes2int(data, offset);

      for (int i = 0; i < this.fnames.length; ++i, offset += 60) {
         String curFName = new String(data, offset, 60);
         curFName.getChars(0, this.fsize[i], this.fnames[i], 0);
      }
   }

   public byte[] directory2bytes( ) {
      // converts and return Directory information into a plain byte array
      // this byte array will be written back to disk
      // note: only meaningfull directory information should be converted
      // into bytes.
      int offset = 0;
      byte [] data = new byte[64 * fsize.length];

      for (int i = 0; i < this.fsize.length; ++i, offset += 4)
         SysLib.int2bytes(this.fsize[i], data, offset);

      for(int i = 0; i < this.fnames.length; ++i, offset += 60) {
         String cur = new String(this.fnames[i], 0, fsize[i]);
         byte [] temp = cur.getBytes();

         for(int j = 0; j < data.length; ++j, offset++) {
            data[j] = temp[j];
         }
      }
      return data;
   }

   public short ialloc( String filename ) {
      // filename is the one of a file to be created.
      // allocates a new inode number for this filename
      short i = 0, retVal = -1;
      while(i < totalDirSize) {
         if(this.fsize[i] == 0) {
            int ins;
            
            if(filename.length() < maxChars)
               ins = filename.length();
            else 
               ins = maxChars;

            this.fsize[i] = ins;
            retVal = i;
            filename.getChars(0, this.fsize[i], this.fnames[i], 0);
            return retVal;
         }
         i++;
      }
      return retVal; // Return error
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
      short i = 0, retVal = -1;
      while(i < totalDirSize) {
         String curFName = new String(this.fnames[i], 0, this.fsize[i]);
         if(filename.length() == this.size[i] && filename.equals(curFName)) {
            retVal = i;
            return retVal;
         }
         i++; 
      }
      return retVal; // Error finding the iNumber corresponding to fName
   }
}