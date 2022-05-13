package ru.surf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;


public class Index {
    TreeMap<String, List<Pointer>> invertedIndex; //может быть private

    ExecutorService pool; //может быть private

    public Index(ExecutorService pool) {
        this.pool = pool;
        invertedIndex = new TreeMap<>();
    }

    public void indexAllTxtInPath(String pathToDir) throws IOException {
        Path of = Path.of(pathToDir);

        BlockingQueue<Path> files = new ArrayBlockingQueue<>(2); //если будет больше чем 2 файла, то будет исключение
        //при попытке добавить третий файл, поэтому нужно либо сделать возможность задать capacity как размер files, либо,
        //если не должно быть больше двух файлов, добавить какую-то логику которая будет обрабатывать попытку добавить третий файл

        try (Stream<Path> stream = Files.list(of)) {
            stream.forEach(files::add);
        }

        pool.submit(new IndexTask(files));
        pool.submit(new IndexTask(files));
        pool.submit(new IndexTask(files)); //зачем три раза?
    }
    //использовать SortedMap?; что если index будет изменён, будет ли правильно возвращать его?
    public TreeMap<String, List<Pointer>> getInvertedIndex() {
        return invertedIndex;
    }

    public List<Pointer> GetRelevantDocuments(String term) {
        //.get() может быть nullable
        return invertedIndex.get(term);
    }

    public Optional<Pointer> getMostRelevantDocument(String term) {
        //.get() может быть nullable
        return invertedIndex.get(term).stream().max(Comparator.comparing(o -> o.count));
    }

    static class Pointer {
        private Integer count;
        private String filePath;

        public Pointer(Integer count, String filePath) {
            this.count = count;
            this.filePath = filePath;
        }

        @Override
        public String toString() {
            return "{" + "count=" + count + ", filePath='" + filePath + '\'' + '}';
        }
    }

    class IndexTask implements Runnable {

        private final BlockingQueue<Path> queue;

        public IndexTask(BlockingQueue<Path> queue) {
            this.queue = queue;
        }

        @Override
        public void run() {
            try {
                Path take = queue.take();
                List<String> strings = Files.readAllLines(take);
                //методы stream разделить по строкам точками
                strings.stream().flatMap(str -> Stream.of(str.split(" "))).forEach(word -> invertedIndex.compute(word, (k, v) -> {
                    if (v == null) return List.of(new Pointer(1, take.toString())); //выделить if в фигурные скобки
                    else {
                        ArrayList<Pointer> pointers = new ArrayList<>();

                        if (v.stream().noneMatch(pointer -> pointer.filePath.equals(take.toString()))) {
                            pointers.add(new Pointer(1, take.toString()));
                        }

                        v.forEach(pointer -> {
                            if (pointer.filePath.equals(take.toString())) { //take.toString() использован уже 4 раза,
                                //создать переменную было бы лучше
                                pointer.count = pointer.count + 1;
                            }
                        });

                        pointers.addAll(v);

                        return pointers;
                    }

                }));

            } catch (InterruptedException | IOException e) { //разделить на разные блоки catch, также использовать interrupt
                throw new RuntimeException(); //сделать возможно своё исключение вместо стандартного Runtime?
            }
        }
    }
}