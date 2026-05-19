# Distributed Inventory Management System

A client-server inventory management system built with Java and JavaFX for CMPE412 (2026).

The server hosts multiple product inventory files and serves them to connected clients over TCP sockets. Clients can browse file contents in a table and request merged statistics computed by a concurrent thread pool.

---

## Student Info

| Field | Value |
|---|---|
| Student Name | [Your Name] |
| Student ID | [Your Student ID] |
| Default Port | `5000 + (last 4 digits of your ID) % 1000` |
| Digit Sum | sum of digits of your student ID |
| VERIFY Hash | `(digit sum × port) % 1000` |

---

## Project Structure

```
system-pro/
├── src/
│   ├── InventoryServer.java     # JavaFX server GUI + accept loop + ClientHandler
│   ├── InventoryClient.java     # JavaFX client GUI + socket protocol
│   ├── InventoryMerger.java     # Thread pool merging + deduplication + stats
│   └── InventoryGenerator.java  # Generates seeded inventory .txt files
├── data/
│   ├── Electronics.txt
│   ├── Clothing.txt
│   ├── Groceries.txt
│   ├── Books.txt
│   └── MergedInventory.txt      # Created automatically on Overview request
├── bin/                         # Compiled .class files (after compile.sh)
├── compile.sh
├── run-server.sh
├── run-client.sh
└── run-generator.sh
```

---

## Requirements

- Java 11 or later (tested on JDK 23)
- JavaFX SDK 21 — download from [gluonhq.com/products/javafx](https://gluonhq.com/products/javafx/) and place at `~/Downloads/javafx-sdk-21.0.11`

---

## How to Run

### 1. Compile

```bash
./compile.sh
```

### 2. Generate inventory data (first time only)

```bash
./run-generator.sh
```

Creates 4 files in `data/`, each with 30 random products + 1 special personalization record, all seeded by the student ID for reproducibility.

### 3. Start the server

```bash
./run-server.sh
```

- JavaFX window opens with the auto-detected IP address
- Port field defaults to your student-ID-based port
- Click **Start** — the log shows:
  ```
  Server started by Student [Your Name] (ID: [Your ID])
  Server started on port [port]
  ```

### 4. Start the client (in a second terminal or on a different PC)

```bash
./run-client.sh
```

- Enter the server's IP and port
- Click **Connect** — status turns green and file buttons appear

---

## Client Usage

| Action | Result |
|---|---|
| Click a file button (e.g. `Electronics.txt`) | Table on the left populates with all product records |
| Click **Get Overview** | Right pane shows average, highest, and lowest prices across all files |
| Click **Verify** | Popup shows the VERIFY hash confirming student identity |
| Click **Disconnect** | Cleanly closes the connection |

---

## How It Works

### Port Formula
```
port = 5000 + (last 4 digits of student ID) % 1000
```

### Seeded Inventory Files
The generator uses the student ID as the seed for `java.util.Random`, producing reproducible files unique to each student. Every file also contains a special record:
```
P9999, StudentID_[YourID]_Demo, 0.01
```
This record has a unique name so it survives the deduplication step and always appears in the merged output.

### Thread Pool Merging
When a client requests Overview, the server uses `Executors.newFixedThreadPool(4)`. One `FileReadTask` (implements `Runnable`) is submitted per file via `executor.execute()`. Tasks read their file concurrently using `BufferedReader` + `FileReader`, then update a shared `ConcurrentHashMap` inside a `synchronized` block — keeping only the highest-priced record per product name (deduplication). After `executor.shutdown()` + `awaitTermination()`, the merged data is written to `MergedInventory.txt` and statistics are computed.

### VERIFY Hash
```
hash = (sum of digits of student ID) × (active port) % 1000
```

---

## Wire Protocol

All communication is plain text over TCP sockets (`BufferedReader` / `PrintWriter`).

```
Client connects  →  Server sends:   FILES <n>
                                    <filename1>
                                    ...

GET <filename>   →  Server sends:   FILE <lineCount>
                                    <line1>
                                    ...
                                    END

OVERVIEW         →  Server sends:   OVERVIEW
                                    AVG <value>
                                    MAX <price>|<name>
                                    MIN <price>|<name>
                                    TOTAL <count>
                                    END

VERIFY           →  Server sends:   VERIFY <hash>

BYE              →  Server closes connection
```

---

## Grading Checklist

| Requirement | Status |
|---|---|
| 4 inventory files, ≥30 records each | ✅ |
| Student ID as RNG seed | ✅ |
| Special `StudentID_[YourID]_Demo` record in every file | ✅ |
| Server GUI: IP, port, Start/Stop, status label | ✅ |
| Activity log with Clear Log button | ✅ |
| All required log entries | ✅ |
| Client GUI: connection panel, dynamic file buttons | ✅ |
| Client GUI: table (ID, Name, Price) | ✅ |
| Client GUI: overview text area | ✅ |
| Verify button + hash popup | ✅ |
| Thread pool (≥4 threads) with `ExecutorService` | ✅ |
| Deduplication (highest price per name) | ✅ |
| `MergedInventory.txt` written on Overview | ✅ |
| Port formula: `5000 + (last4 % 1000)` | ✅ |
| Startup log: `"Server started by Student [Name] (ID: [ID])"` | ✅ |
| Exception handling for all network/file errors | ✅ |
