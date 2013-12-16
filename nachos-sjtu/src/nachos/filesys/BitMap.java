package nachos.filesys;

public class BitMap {
	byte[] bitmap;	// 1 means free; 0 means used;
	int size;
	static final int ByteShift = 3;
	static final int ByteModMask = 0x7;
	BitMap(int numbits) {
		size = numbits >> ByteShift;
		if ((numbits & ByteModMask) != 0) ++size;
		bitmap = new byte[size];	// FIXME: it should be all zero
	}
	
	boolean set(int pos, boolean use) {
		if (pos < 0 || pos >= size) return false;
		int index = pos >> ByteShift;
		int rem = pos & ByteModMask;
		byte mask = (byte) (1<<rem);
		if (!use) 
			bitmap[index] |= mask;
		else 
			bitmap[index] &= (byte) ~mask;
		
		return true;
	}
	
	void writeBack(File file) {
		file.write(0, bitmap, 0, size);
	}
	
	void readFrom(File file) {
		file.read(0, bitmap, 0, size);
	}
	boolean isFree(int pos) {
		int index = pos >> ByteShift;
		int rem = pos & ByteModMask;
		if ((bitmap[index] & (byte)(1<<rem)) != 0) return true;
		return false;
	}
}
