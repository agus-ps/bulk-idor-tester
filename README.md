# Bulk IDOR Tester

**Bulk IDOR Tester** is a Burp Suite extension designed to simplify and automate the detection of Broken Access Control (BAC) and Insecure Direct Object References (IDOR) vulnerabilities.

It allows you to capture multiple requests, modify their headers in bulk (e.g., to swap Session A for Session B or remove it entirely), and analyze the responses with an intelligent visual system that highlights suspicious behaviors.

![Bulk IDOR Tester UI](screenshots/ui.png)

## 🔥 Key Features

The plugin consists of three main modules integrated into a native interface:

### 1. Bulk Header Manipulation
Define header rules that will apply to all selected requests before sending them:
* **Add/Edit:** Inject session headers from another user (e.g., `Cookie`, `Authorization`).
* **Remove:** Delete specific headers to test unauthenticated access.

### 2. Traffic Light System (Visual Feedback)
Forget about manually checking every status code. The result table colors itself automatically:
* 🔴 **Red (Danger):** The modified request returned the same success status (2xx) as the original one. Indicates a potential IDOR.
* 🟢 **Green (Safe):** The server blocked access (401/403) while the original request was successful.
* 🟡 **Yellow (Warning):** The behavior changed drastically (e.g., from 200 to 500).

### 3. Smart Analysis
Beyond the status code, the extension calculates key metrics to avoid false positives:
* **Similarity %:** Uses the Levenshtein algorithm to compare the original response body vs. the new one.
* **Length Delta:** Shows the exact difference in bytes (e.g., `(+50)` or `(-120)`).

---

## 🚀 Installation

### From Releases (Recommended)
1. Go to the [Releases](../../releases) section of this repository.
2. Download the latest `bulk-idor-tester-x.x.x.jar` file.
3. Open Burp Suite and navigate to **Extensions** -> **Installed**.
4. Click **Add**, select **Java**, and load the `.jar` file.

### Build from Source
You need Java JDK 17+ and Maven installed.

```bash
git clone [https://github.com/agus-ps/bulk-idor-tester.git](https://github.com/agus-ps/bulk-idor-tester.git)
cd bulk-idor-tester
mvn clean package -DskipTests
```

The compiled file will be generated in the `target/` folder.

-----

## 📖 Usage Guide

1.  **Send Requests:**
    Right-click on any request (from Proxy, Repeater, or Logger) and select:
    `Extensions` -\> `Send to Bulk IDOR Tester`.

2.  **Configure Attacker:**
    In the extension tab, open the right side menu **"Headers"**.

      * Add your attacker's headers (e.g., `Cookie: session=evil_user`).
      * Or configure headers to remove (e.g., remove `Authorization` to test anonymous access).

3.  **Launch Attack:**
    Click the orange **Start Attack** button.

4.  **Analyze:**
    Check the table. Focus on **Red** rows or those with a high **Similarity** percentage.

-----

## ⚠️ Disclaimer

This software has been created solely for academic research purposes and for the development of effective defensive techniques. It is not intended to be used to attack systems except where explicitly authorized. The project maintainers are not responsible or liable for misuse of the software. Use responsibly.

## 📄 License

This project is licensed under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for details.

