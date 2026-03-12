# Wovely

## Description 
Wovely is a web application designed to provide users with a space in which they can sell or buy hand-made goods. The web app ensures careful stock management, wallet capabilities, a review system and different delivery options, which can present a carbon-emission free mode.

---

### Story 1: Platform Administration & Moderation
**User Story:** As an Admin, I want a comprehensive dashboard to moderate content and assist users, so that I can maintain a safe, functional, and high-quality marketplace.

**Key Features:**
* **Listing Management:** Ability to approve, edit, or delete ads that violate terms.
* **User Governance:** Ability to warn, suspend, or ban users based on disruptive behavior.
* **Order Intervention:** Access to order details to manually adjust quantities, prices, or statuses in case users need assistance.
* **Safety Monitoring:** A "Flagged Content" queue for reported chat logs and products.

**Acceptance Criteria:**
* Admin can search for users by email or username.
* Admin can view chat logs only if a conversation has been officially reported.
* All manual order changes made by admins must be logged in an audit trail.

---

### Story 2: Seller Inventory & Logistics
**User Story:** As a Seller, I want an intuitive inventory and order management system, so that I can focus on my craft rather than technical data entry or shipping logistics.

**Key Features:**
* **"No-Code" Lookup:** Search for items by name or photo while creating an order, avoiding the need for manual SKU/code entry.
* **Smart Inventory:** Automatic stock deduction upon purchase and real-time restocking if an order is canceled.
* **Integrated Logistics:** A "Ship with Platform" option where shipping is handled automatically to remove the need for manual carrier negotiation.
* **Order Flexibility:** Ability to modify order details (e.g., color or size) if requested by the customer after placement but before processing.
* **Status Tracking:** Real-time visibility of the order lifecycle (e.g., Paid -> Processing -> Shipped).

**Acceptance Criteria:**
* Inventory count must update automatically in the storefront once an order is placed.
* The "Order Edit" button must be disabled once the order status moves beyond "Processing."
* Sellers must be able to see a clear status timeline for every active order.

---

### Story 3: Buyer Experience & Sustainability
**User Story:** As a Buyer, I want a personalized, transparent, and eco-conscious shopping experience, so that I can support artists while minimizing my environmental impact.

**Key Features:**
* **Discovery Tools:** Browse handmade products by grouping categories and viewing social proof (reviews/comments from previous purchasers).
* **Green Shipping:** Easy shipping options with a focus on low to zero CO2 emissions (e.g., carbon-neutral delivery or local pickup).
* **Pre-Purchase Chat:** A built-in messaging tool to discuss questions or custom requests directly with the seller.
* **Social Proof:** A review system that displays comments and ratings from verified buyers.

**Acceptance Criteria:**
* Search results must be filterable by "Zero-Emission" or "Eco-friendly" delivery.
* The chat interface must support real-time notifications for both parties.
* Reviews must clearly distinguish "Verified Purchase" entries to ensure trust.
