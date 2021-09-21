package sachin.com.example;

import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

public class TestPath {

    public static void main(String[] args) {
        Iterable<Path> rootDirectories = FileSystems.getDefault().getRootDirectories();
        Iterator<Path> iterator = rootDirectories.iterator();
        while (iterator.hasNext()) {
            Path next = iterator.next();
            System.out.println(next.toUri());
        }
    }

    private void test() {
        Path absolutePath1 = Paths.get("C:/Lokesh/Setup/workspace/NIOExamples/src", "sample.txt");
        Iterable<Path> rootDirectories = FileSystems.getDefault().getRootDirectories();
    }

    public void testC() throws Exception {

        RandomAccessFile file = new RandomAccessFile("test.txt", "r");

        FileChannel channel = file.getChannel();
        MappedByteBuffer mappedByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        mappedByteBuffer.load();
        for (int i = 0; i < mappedByteBuffer.limit(); i++) {
            System.out.println((char) mappedByteBuffer.get());
        }
        mappedByteBuffer.clear();
        channel.close();
        file.close();

    }


}
