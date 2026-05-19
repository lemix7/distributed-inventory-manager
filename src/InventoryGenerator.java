import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Random;

// =====================================================================
// PERSONALIZATION (CMPE412 anti-plagiarism)
//   Student Name : ahmed mohamed
//   Student ID   : 22203941
//
// The student ID is used as the SEED for java.util.Random so that the
// generated inventory files are reproducible and unique to this student.
// In addition, every file contains one special record whose ProductName
// embeds the full student ID (P9999, StudentID_22203941_Demo, 0.01).
// This special record must survive the deduplication step done by the
// thread pool (it does, because its name is unique across all files).
// =====================================================================
public class InventoryGenerator {

    // Full student ID — used as the seed and embedded in the special record
    static final long   STUDENT_ID_SEED = 22203941L;
    static final String STUDENT_ID_STR  = "22203941";

    public static void main(String[] args) throws Exception {

        // Seed the RNG with the student ID so the data is reproducible
        Random rng = new Random(STUDENT_ID_SEED);

        // The 4 inventory categories required by the project (>=4)
        String[] categories = {"Electronics", "Clothing", "Groceries", "Books"};

        // Product-name pools per category. Some names INTENTIONALLY repeat
        // across categories (e.g. "Wireless Mouse" in Electronics &
        // Accessories-style overlap) so that the merger's deduplication
        // step actually has work to do, matching the PDF example.
        String[] electronicsNames = {
            "Smart TV", "Gaming Laptop", "Noise Cancelling Headphones",
            "Mechanical Keyboard", "4K Webcam", "Wireless Charger",
            "Portable SSD", "Smart Watch", "Tablet", "Graphics Card",
            "Wi-Fi Router", "Action Camera", "Smart Speaker",
            "Portable Projector"
        };
        String[] clothingNames = {
            "Polo Shirt", "Cargo Pants", "Leather Jacket", "Running Shoes",
            "Baseball Cap", "Winter Coat", "Compression Socks", "Canvas Belt",
            "Wool Scarf", "Fleece Gloves",
            "Smart Watch",      // duplicate name on purpose
            "Tracksuit", "Swim Shorts", "Summer Dress"
        };
        String[] groceriesNames = {
            "Sourdough Bread", "Almond Milk", "Free Range Eggs", "Cheddar Cheese",
            "Organic Apples", "Plantain", "Basmati Rice", "Whole Wheat Pasta",
            "Frozen Chicken", "Cherry Tomatoes",
            "Extra Virgin Olive Oil", "Ground Coffee", "Green Tea",
            "Portable Projector"    // duplicate name on purpose
        };
        String[] booksNames = {
            "Clean Code", "Design Patterns", "Introduction to Algorithms",
            "The Pragmatic Programmer", "Computer Networks", "Database Internals",
            "Discrete Mathematics", "Calculus Made Easy", "University Physics",
            "Organic Chemistry",
            "Smart Watch",          // duplicate name on purpose
            "Sapiens", "The Alchemist", "Atomic Habits"
        };
        String[][] pools = { electronicsNames, clothingNames,
                             groceriesNames, booksNames };

        // Price range (min, max) per category — Electronics expensive, Groceries cheap
        double[][] priceRanges = {
            { 49.99,  1499.99 },   // Electronics
            {  9.99,   199.99 },   // Clothing
            {  0.99,    29.99 },   // Groceries
            {  7.99,    59.99 }    // Books
        };

        // Generate one file per category
        for (int i = 0; i < categories.length; i++) {
            String filename = categories[i] + ".txt";
            String[] pool   = pools[i];
            double   pMin   = priceRanges[i][0];
            double   pMax   = priceRanges[i][1];

            try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {

                // 30 normal records per file (>= 30 required by PDF)
                int idStart = 1000 + i * 100;          // distinct ID range per file
                for (int j = 0; j < 30; j++) {
                    int    pid   = idStart + j + 1;
                    String pname = pool[rng.nextInt(pool.length)];
                    double price = pMin + rng.nextDouble() * (pMax - pMin);
                    writer.printf("P%d, %s, %.2f%n", pid, pname, price);
                }

                // 31st line: the SPECIAL personalised record.
                // Its name is unique across all files, so it survives
                // the "keep highest price per name" deduplication.
                writer.printf("P9999, StudentID_%s_Demo, %.2f%n",
                              STUDENT_ID_STR, 0.01);
            }

            System.out.println("Created: " + filename);
        }

        System.out.println("Done. Generated " + categories.length
                           + " inventory files (seed = " + STUDENT_ID_SEED + ").");
    }
}
