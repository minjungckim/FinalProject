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
   public final static int iNodeSize = 32;       // fix to 32 bytes
   private final static int directSize = 11;      // # direct pointers
   private final static int totalInodes = Disk.blockSize / iNodeSize;

   public int length;                             // file size in bytes
   public short count;                            // # file-table entries pointing to this
   public short flag;                             // 0 = unused, 1 = used, ...
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
      int blockNum, offset;
      byte [] data = new byte[Disk.blockSize];

      // read corresponding block into memory
      blockNum = 1 + iNumber / 512;
      SysLib.rawread(blockNum, data);

      // Use offset to read iNode data from block
      offset = (iNumber % 16) * iNodeSize;

      length = SysLib.bytes2int(data, offset);
      offset += 4;

      count = SysLib.bytes2short(data, offset);
      offset += 2;

      flag = SysLib.bytes2short(data, offset);
      offset += 2;

      for (int i = 0; i < directSize; ++i, offset += 2)
         direct[i] = SysLib.bytes2short(data, offset);

      indirect = SysLib.bytes2short(data, offset);
      offset += 2;
   }

   /* 
   ** toDisk method takes in an iNumber and 
   ** writes the information of the corresponding 
   ** Inode back into the disk.
   */
   int toDisk( short iNumber ) {                  // save to disk as the i-th inode
      int blockNum = -1, offset = 0;
      byte [] data = new byte[iNodeSize];

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

      blockNum = 1 + iNumber / 16;
      byte [] tempData = new byte[512];
      SysLib.rawread(blockNum, tempData);

      // use offset to write iNode data to block
      offset = (iNumber % 16) * iNodeSize;

      // write block back to disk and return num bytes written
      System.arraycopy(data, 0, tempData, offset, iNodeSize);
      SysLib.rawwrite(blockNum, tempData);

      return 0;
   }

   boolean setBlock(short iNumber) {
      byte [] data = new byte[Disk.blockSize];

      if (iNumber > 0 && indirect != -1) {
         int index = 0;
         while(index < directSize) {
            if(direct[index] == -1)
               return false;
            index++;
         }
         index = 0;

         indirect = iNumber;
         while(index < 512 / 2) {
            SysLib.short2bytes((short)-1, data, index * 2);
            index++;
         }
        SysLib.rawwrite(iNumber, data);
        return true;
      } 
      return false;
   }

   int getBlock(int offset) {
      int retval = offset / 512;

        if (retval < 11){
            return direct[retval];
        }
        if (this.indirect < 0){
            return -1;
        }

        byte[] data = new byte[512];
        SysLib.rawread(this.indirect, data);

        int blockSpace = (retval - 11) * 2;
        return SysLib.bytes2short(data, blockSpace);
   }

   int getIndirectBlock() {
      return indirect;
   }

   int getIndexBlockNumber(int iNumber, short offset){
      int block = iNumber / Disk.blockSize;
      if(iNumber > 0) {

         if (block < directSize || ((block > 0 ) && (direct[block - 1 ] == -1))){
            if(direct[block] >= 0){
               return -1;
            }
            else {
               direct[block] = offset;
               return 0;
            }
        }
        if (indirect < 0){
            return -1;
        }
        else {
            byte[] data = new byte[Disk.blockSize];
            SysLib.rawread(indirect, data);

            int space = (block - directSize) * 2;
            if ( SysLib.bytes2short(data, space) > 0){
                return -1;
            }
            else
            {
                SysLib.short2bytes(offset, data, space);
                SysLib.rawwrite(indirect, data);
            }
        }
        return 0;
      }
      return -1;
   }

}