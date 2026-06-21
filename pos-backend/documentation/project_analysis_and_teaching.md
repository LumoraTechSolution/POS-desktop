# Lumora POS: Project Analysis & Learning Guide

Welcome to the **Lumora POS System** development journey! Since you are new to POS systems, this guide will break down "The Why" and "The How" of our architecture using simple analogies and clear technical reasoning.

---

## 1. The Technology Stack: "The Building Blocks"

Think of a software system like a **restaurant**:

### **Backend: Spring Boot (The Kitchen)**

- **Why?** The kitchen is where the "heavy lifting" happens. Spring Boot is incredibly fast and reliable for handling complex rules (like tax calculations or stock management).
- **Role**: It acts as the "Brain" and the "Guard". It decides who can enter (Security) and processes all the data logic.

### **Frontend: Next.js + Tailwind CSS (The Dining Area)**

- **Why?** This is what the user (Cashier/Admin) interacts with. It needs to be beautiful, fast, and work on tablets as well as desktops.
- **Role**: It handles the "User Experience". When a cashier clicks a button, Next.js sends that request to the kitchen.

### **Database: PostgreSQL (The Ledger)**

- **Why?** In a POS, money and stock data are critical. You cannot lose even one cent. PostgreSQL is "ACID-compliant," which is a fancy way of saying it is **extremely safe** for financial data.
- **Role**: It is the "Permanent Memory" of the system.

### **Environment: Docker (The Shipping Container)**

- **Why?** Have you ever had a game or app that worked on one computer but not another? This is the "it works on my machine" problem. Docker solves this by putting everything the software needs (like the database and Redis) into a "container."
- **Role**: It ensures the system runs **exactly the same** on your laptop, my laptop, and the final production server.

---

## 2. Managing Data: "The App's Memory"

### **State Management: Zustand**

- **What is it?** Zustand is a small, fast library we use in the frontend to remember things while the user is clicking around.
- **Why?** Imagine a cashier logs in. Every part of the app (the sidebar, the checkout screen, the profile page) needs to know who is logged in.
- **Simple Term**: Think of Zustand as a **"Bulletin Board"** in the middle of our app. Any component can pin a note there (like "User is Admin") and any other component can read it instantly without having to ask the parent.

---

## 3. The Architectural Approach: "The Strategy"

We use a **Clean Layered Architecture**. Imagine organized drawers in a desk:

1.  **Controller (The Counter)**: Receives the customer's request. It doesn't cook; it just takes the order.
2.  **Service (The Chef)**: The logic layer. This is where we calculate totals, check if we have enough stock, and apply discounts.
3.  **Repository (The Pantry)**: This layer talks directly to the database to fetch or save items.

### **Key Concept: Multi-Tenancy**

In the real world, many different stores (Tenants) might use our software.

- **Simple Term**: Think of an apartment building. Everyone lives in the same building (the software), but everyone has their own locked door (Tenant ID). Store A can never see Store B’s sales.

### **Key Concept: RBAC (Role-Based Access Control)**

- **Simple Term**: A **Cashier** only has the "Sale" key. An **Owner** has the "Master Key" (Admin) to see profit reports and change prices. We control this in the database using **Roles** and **Permissions**.

---

## 3. The Database Schema: "The Skeleton"

Everything in our system is connected. Here are the core tables we've built so far:

- **`tenants`**: Stores the name and settings of each business.
- **`users`**: The employees. They have an email for the office and a **PIN** for the fast-paced checkout counter.
- **`roles` & `permissions`**: The rules. (e.g., "Role: Cashier" has "Permission: Create_Sale").
- **`refresh_tokens`**: A security feature that keeps users logged in safely without them needing to type their password every 5 minutes.

---

## 4. How the "Login" Actually Works (Step 2)

We just finished the Authentication part. Here is the flow:

1.  **User enters Email/Password or PIN.**
2.  **Backend checks the Database**: "Does this match?"
3.  **Backend issues a JWT (Digital Badge)**: This badge contains the User's ID and what they are allowed to do.
4.  **Frontend holds the Badge**: For every future request (like "Show me the products"), the frontend shows this badge so the backend knows it's a valid request.

---

## 5. Next Steps: "The Road Map"

Now that the "Door" (Login) is built, we proceed to:

1.  **Inventory (The Warehouse)**: Managing products, categories, and stock levels.
2.  **Sales (The Transaction)**: The actual "Checkout" process where we scan items and take payments.
3.  **Reporting (The Dashboard)**: Showing the owner how much money they made today.

---

### **Learning Tip** 💡

Don't try to understand every line of code at once. Focus on the **Flow**:

- _User Action (UI) → Request (API) → Logic (Service) → Save (Database)_.
