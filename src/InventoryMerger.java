import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// =====================================================================
// Thread-pool based merger (the 25-point centrepiece of the project).
//
//  1. Reads all inventory files CONCURRENTLY using a fixed thread pool
//     of 4 threads (PDF requires >= 4).
//     Each file is handled by a FileReadTask (implements Runnable),
//     submitted via executor.execute() — matching the course reference.
//  2. Deduplicates: for every product NAME, keep ONLY the record with
//     the highest price (see the Wireless Mouse example in the PDF).
//     The shared bestByName map is updated inside a synchronized block
//     to prevent race conditions (see Thread Codes reference).
//  3. Writes the merged records into MergedInventory.txt.
//  4. Computes overall average / highest / lowest price and returns
//     the result as a MergeResult.
// =====================================================================
public class InventoryMerger {

    public static final String MERGED_FILE = "MergedInventory.txt";

    // Holds the result of one merge run (avg/max/min + names)
    public static class MergeResult {
        public final double avg;
        public final double maxPrice;
        public final String maxName;
        public final double minPrice;
        public final String minName;
        public final int    totalProducts;

        public MergeResult(double avg, double maxPrice, String maxName,
                           double minPrice, String minName, int totalProducts) {
            this.avg           = avg;
            this.maxPrice      = maxPrice;
            this.maxName       = maxName;
            this.minPrice      = minPrice;
            this.minName       = minName;
            this.totalProducts = totalProducts;
        }
    }

    // Merge entry point — called by the server when a client asks for OVERVIEW
    public static MergeResult merge(List<String> filenames) throws Exception {

        // Shared map: productName -> best Product (highest price).
        // ConcurrentHashMap is used so multiple threads can read/check safely;
        // the "keep highest price" update is wrapped in a synchronized block.
        ConcurrentHashMap<String, Product> bestByName = new ConcurrentHashMap<>();

        // 1) Submit one FileReadTask per file to a fixed thread pool of 4 workers.
        //    This matches the executor.execute(new Task(...)) pattern from the reference.
        ExecutorService pool = Executors.newFixedThreadPool(4);

        for (String fname : filenames) {
            pool.execute(new FileReadTask(fname, bestByName));
        }

        // Shut down the pool and wait for all tasks to finish before continuing.
        // This is the shutdown() + awaitTermination() pattern from the reference.
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        // 2) Write merged records to MergedInventory.txt
        try (PrintWriter writer = new PrintWriter(new FileWriter(MERGED_FILE))) {
            for (Product p : bestByName.values()) {
                writer.printf("%s, %s, %.2f%n", p.id, p.name, p.price);
            }
        }

        // 3) Compute overall average / highest / lowest from the merged map
        double total    = 0.0;
        double maxPrice = -Double.MAX_VALUE;
        double minPrice =  Double.MAX_VALUE;
        String maxName  = "";
        String minName  = "";
        int    count    = 0;

        for (Product p : bestByName.values()) {
            total += p.price;
            count++;
            if (p.price > maxPrice) { maxPrice = p.price; maxName = p.name; }
            if (p.price < minPrice) { minPrice = p.price; minName = p.name; }
        }

        double avg = (count == 0) ? 0.0 : total / count;

        return new MergeResult(avg, maxPrice, maxName, minPrice, minName, count);
    }
}

// ---------------------------------------------------------------------
// One Runnable task per inventory file — matches the course reference
// pattern:  class Task implements Runnable { ... }
//
// Each task reads one file, parses every product line, then updates
// the shared bestByName map inside a synchronized block so that only
// the highest-priced record per product name survives (deduplication).
// ---------------------------------------------------------------------
class FileReadTask implements Runnable {

    private final String filename;
    // Shared across all FileReadTask threads — must update under a lock
    private final ConcurrentHashMap<String, Product> bestByName;

    public FileReadTask(String filename, ConcurrentHashMap<String, Product> bestByName) {
        this.filename    = filename;
        this.bestByName  = bestByName;
    }

    @Override
    public void run() {
        List<Product> products = new ArrayList<>();

        // Read the file using BufferedReader + FileReader (course reference style)
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;        // skip blank lines

                // Format: "P1001, Smart TV, 749.99"
                String[] data = line.split(",");
                if (data.length < 3) continue;             // skip malformed lines

                String id    = data[0].trim();
                String name  = data[1].trim();
                double price = Double.parseDouble(data[2].trim());

                products.add(new Product(id, name, price));
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filename + ": " + e.getMessage());
        }

        // Update the shared map: keep the highest-priced product per name.
        // synchronized block prevents race conditions when two threads try to
        // update the same product name at the same time (reference pattern).
        synchronized (bestByName) {
            for (Product p : products) {
                Product current = bestByName.get(p.name);
                if (current == null || p.price > current.price) {
                    bestByName.put(p.name, p);
                }
            }
        }

        System.out.println("Thread " + Thread.currentThread().getName()
                           + " finished reading " + filename
                           + " (" + products.size() + " products)");
    }
}

// ---------------------------------------------------------------------
// Simple product POJO — kept in the same file (course reference style).
// ---------------------------------------------------------------------
class Product {
    public final String id;
    public final String name;
    public final double price;

    public Product(String id, String name, double price) {
        this.id    = id;
        this.name  = name;
        this.price = price;
    }
}
