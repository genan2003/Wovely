#!/usr/bin/env python3
"""Generates postman/Wovely.postman_collection.json and postman/Wovely.seed.postman_collection.json.

Edit the request definitions here, then run:  python3 scripts/postman/build_collection.py
The JSON files are the artefacts Postman/newman use; this script keeps them consistent
(auth headers, variable names, shared test snippets).

The main collection is an ordered scenario: each folder relies on variables set by earlier ones
(tokens, ids). Run the whole collection, not single folders, unless you set the variables yourself.
"""
import json
import os
import uuid

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT_DIR = os.path.join(ROOT, "postman")

# --------------------------------------------------------------------------- helpers

def js(*lines):
    return [l for block in lines for l in block.strip("\n").split("\n")]


def status(*codes):
    if len(codes) == 1:
        return f"""pm.test("status is {codes[0]}", () => pm.response.to.have.status({codes[0]}));"""
    arr = ", ".join(str(c) for c in codes)
    return f"""pm.test("status is one of [{arr}]", () => pm.expect(pm.response.code).to.be.oneOf([{arr}]));"""


def req(name, method, url, *, token=None, body=None, tests="", pre="", description=""):
    headers = [{"key": "Content-Type", "value": "application/json"}] if body is not None else []
    item = {
        "name": name,
        "request": {
            "method": method,
            "header": headers,
            "url": url,
            "description": description,
        },
        "event": [],
    }
    if token:
        item["request"]["auth"] = {
            "type": "bearer",
            "bearer": [{"key": "token", "value": "{{" + token + "}}", "type": "string"}],
        }
    else:
        item["request"]["auth"] = {"type": "noauth"}
    if body is not None:
        item["request"]["body"] = {
            "mode": "raw",
            "raw": json.dumps(body, indent=2) if not isinstance(body, str) else body,
            "options": {"raw": {"language": "json"}},
        }
    if pre:
        item["event"].append({"listen": "prerequest", "script": {"type": "text/javascript", "exec": js(pre)}})
    if tests:
        item["event"].append({"listen": "test", "script": {"type": "text/javascript", "exec": js(tests)}})
    return item


def folder(name, description, items):
    return {"name": name, "description": description, "item": items}


CORE = "{{baseUrlCore}}"
PRODUCTS = "{{baseUrlProducts}}"
INVENTORY = "{{baseUrlInventory}}"
JSON_OK = 'const body = pm.response.json();'
NO_TOKEN_REJECTED = status(401, 403) + "\n// TODO(PLAN 1.9): products/inventory return 403 without an entry point; tighten to 401."

# --------------------------------------------------------------------------- Auth

def signin(name, user, pwd, token_var, id_var):
    return req(
        f"Sign in as {name}", "POST", f"{CORE}/api/auth/signin",
        body={"username": user, "password": pwd},
        tests=f"""
{status(200)}
{JSON_OK}
pm.test("returns a bearer token, id and roles", () => {{
  pm.expect(body.accessToken).to.be.a("string").and.not.empty;
  pm.expect(body.tokenType).to.eql("Bearer");
  pm.expect(body.id).to.be.a("string").and.not.empty;
  pm.expect(body.roles).to.be.an("array").that.is.not.empty;
}});
pm.collectionVariables.set("{token_var}", body.accessToken);
pm.collectionVariables.set("{id_var}", body.id);
""")

auth = folder("Auth", "Signs in the seeded users and stores tokens/ids used by every later folder.", [
    signin("admin", "admin", "admin123", "adminToken", "adminId"),
    signin("seller", "seller", "seller123", "sellerToken", "sellerId"),
    signin("buyer", "testuser", "user123", "buyerToken", "buyerId"),
    req("Seller token carries ROLE_SELLER", "POST", f"{CORE}/api/auth/signin",
        body={"username": "seller", "password": "seller123"},
        tests=f"""{status(200)}
pm.test("roles contain ROLE_SELLER", () => pm.expect(pm.response.json().roles).to.include("ROLE_SELLER"));"""),
    req("Sign up new buyer", "POST", f"{CORE}/api/auth/signup",
        pre="""
const suffix = Date.now().toString(36);
pm.collectionVariables.set("newUsername", ("qa_" + suffix).slice(0, 20));
pm.collectionVariables.set("newEmail", "qa_" + suffix + "@wovely.test");
""",
        body={"username": "{{newUsername}}", "email": "{{newEmail}}", "password": "secret123"},
        tests=f"""{status(200)}
pm.test("success message", () => pm.expect(pm.response.json().message).to.match(/registered successfully/i));"""),
    req("Sign in as new buyer", "POST", f"{CORE}/api/auth/signin",
        body={"username": "{{newUsername}}", "password": "secret123"},
        tests=f"""{status(200)}
{JSON_OK}
pm.test("new user has only ROLE_USER", () => pm.expect(body.roles).to.eql(["ROLE_USER"]));
pm.collectionVariables.set("newUserToken", body.accessToken);
pm.collectionVariables.set("newUserId", body.id);"""),
    req("Sign up with taken username → 400", "POST", f"{CORE}/api/auth/signup",
        body={"username": "seller", "email": "other@wovely.test", "password": "secret123"},
        tests=f"""{status(400)}
pm.test("explains username is taken", () => pm.expect(pm.response.json().message).to.match(/username is already taken/i));"""),
    req("Sign up with invalid email/short password → 400", "POST", f"{CORE}/api/auth/signup",
        body={"username": "ab", "email": "not-an-email", "password": "123"},
        tests=status(400)),
    req("Sign in with wrong password → 401", "POST", f"{CORE}/api/auth/signin",
        body={"username": "seller", "password": "wrong-password"},
        tests=status(401)),
    req("Protected endpoint without token → 401", "GET", f"{CORE}/api/admin/users",
        tests=status(401)),
    req("Garbage token → 401", "GET", f"{CORE}/api/admin/users", token="garbageToken",
        tests=status(401),
        pre='pm.collectionVariables.set("garbageToken", "not.a.jwt");'),
    req("Get own profile", "GET", f"{CORE}/api/user/profile/{{{{sellerId}}}}", token="sellerToken",
        tests=f"""{status(200)}
pm.test("profile matches seller", () => pm.expect(pm.response.json().username).to.eql("seller"));"""),
    req("Update own profile", "PUT", f"{CORE}/api/user/profile/{{{{sellerId}}}}", token="sellerToken",
        body={"city": "Sibiu", "region": "Sibiu", "makerStory": "Weaving since 2010."},
        tests=status(200)),
    req("Public seller profile", "GET", f"{CORE}/api/public/users/seller/{{{{sellerId}}}}",
        tests=f"""{status(200)}
{JSON_OK}
pm.test("shows updated city", () => pm.expect(body.city).to.eql("Sibiu"));
pm.test("does not expose email", () => pm.expect(body).to.not.have.property("email"));"""),
])

# --------------------------------------------------------------------------- Products

products = folder("Products", "Catalog, categories, seller product management and admin listing moderation.", [
    req("List categories", "GET", f"{PRODUCTS}/api/categories",
        tests=f"""{status(200)}
pm.test("seeded categories exist", () => pm.expect(pm.response.json()).to.be.an("array").that.is.not.empty);"""),
    req("List root categories", "GET", f"{PRODUCTS}/api/categories/roots",
        tests=f"""{status(200)}
const roots = pm.response.json();
pm.test("roots is a non-empty array", () => pm.expect(roots).to.be.an("array").that.is.not.empty);
pm.collectionVariables.set("rootCategoryId", roots[0].id);
pm.collectionVariables.set("rootCategoryName", roots[0].name);"""),
    req("List sub-categories", "GET", f"{PRODUCTS}/api/categories/children/{{{{rootCategoryId}}}}",
        tests=f"""{status(200)}
pm.test("is an array", () => pm.expect(pm.response.json()).to.be.an("array"));"""),
    req("Seller creates product", "POST", f"{PRODUCTS}/api/products", token="sellerToken",
        body={
            "name": "QA Woven Basket", "description": "Hand-woven willow basket (Postman test).",
            "price": 42.5, "sellerId": "{{sellerId}}",
            "imageUrl": "https://images.unsplash.com/photo-1519710164239-da123dc03ef4",
            "category": "{{rootCategoryName}}", "categoryPath": "{{rootCategoryName}}",
            "materials": ["willow"], "city": "Sibiu", "region": "Sibiu",
            "co2EmissionScore": "Low", "shippingMethod": "Carbon Neutral Courier",
            "isHandmade": True, "stockQuantity": 5, "lowStockThreshold": 2,
        },
        tests=f"""{status(201)}
{JSON_OK}
pm.test("belongs to the seller", () => pm.expect(body.sellerId).to.eql(pm.collectionVariables.get("sellerId")));
pm.test("stock is 5", () => pm.expect(body.stockQuantity).to.eql(5));
// NOTE(PLAN 1.2): today new products are auto-APPROVED; after the fix they start PENDING.
pm.test("has a moderation status", () => pm.expect(body.status).to.be.oneOf(["PENDING", "APPROVED"]));
pm.collectionVariables.set("productId", body.id);
pm.collectionVariables.set("productPrice", body.price);"""),
    req("Admin approves product", "PUT", f"{PRODUCTS}/api/products/admin/{{{{productId}}}}/status", token="adminToken",
        body={"status": "APPROVED"},
        tests=f"""{status(200)}
pm.test("status is APPROVED", () => pm.expect(pm.response.json().status).to.eql("APPROVED"));"""),
    req("Buyer cannot moderate products → 403", "PUT", f"{PRODUCTS}/api/products/admin/{{{{productId}}}}/status", token="buyerToken",
        body={"status": "REJECTED"}, tests=status(403)),
    req("Admin lists pending products", "GET", f"{PRODUCTS}/api/products/admin/pending", token="adminToken",
        tests=f"""{status(200, 204)}
if (pm.response.code === 200) pm.test("is an array", () => pm.expect(pm.response.json()).to.be.an("array"));"""),
    req("Catalog contains approved product", "GET", f"{PRODUCTS}/api/products",
        tests=f"""{status(200)}
const list = pm.response.json();
pm.test("only APPROVED products are listed", () => list.forEach(p => pm.expect(p.status).to.eql("APPROVED")));
pm.test("contains the new product", () => pm.expect(list.map(p => p.id)).to.include(pm.collectionVariables.get("productId")));"""),
    req("Catalog filter: eco + price range", "GET", f"{PRODUCTS}/api/products?isEco=true&minPrice=10&maxPrice=100&city=sib",
        tests=f"""{status(200)}
const list = pm.response.json();
pm.test("all results match filters", () => list.forEach(p => {{
  pm.expect(p.co2EmissionScore.toLowerCase()).to.eql("low");
  pm.expect(p.price).to.be.within(10, 100);
  pm.expect(p.city.toLowerCase()).to.include("sib");
}}));
pm.test("new product matches", () => pm.expect(list.map(p => p.id)).to.include(pm.collectionVariables.get("productId")));"""),
    req("Get product by id", "GET", f"{PRODUCTS}/api/products/{{{{productId}}}}",
        tests=f"""{status(200)}
pm.test("name matches", () => pm.expect(pm.response.json().name).to.eql("QA Woven Basket"));"""),
    req("Get unknown product → 404", "GET", f"{PRODUCTS}/api/products/000000000000000000000000",
        tests=status(404)),
    req("Seller inventory view", "GET", f"{PRODUCTS}/api/products/seller/{{{{sellerId}}}}", token="sellerToken",
        tests=f"""{status(200)}
{JSON_OK}
pm.test("has items and counters", () => {{
  pm.expect(body.items).to.be.an("array");
  pm.expect(body).to.include.keys("totalItems", "lowStockCount", "outOfStockCount");
}});
pm.test("contains the new product", () => pm.expect(body.items.map(i => i.id)).to.include(pm.collectionVariables.get("productId")));"""),
    req("Seller dashboard", "GET", f"{PRODUCTS}/api/products/seller/{{{{sellerId}}}}/dashboard", token="sellerToken",
        tests=f"""{status(200)}
pm.test("has summary keys", () => pm.expect(pm.response.json()).to.include.keys("totalProducts", "inStock", "lowStock", "outOfStock", "categoryBreakdown"));"""),
    req("Seller sets stock to 10", "PUT", f"{PRODUCTS}/api/products/seller/{{{{sellerId}}}}/product/{{{{productId}}}}/stock", token="sellerToken",
        body={"quantity": 10},
        tests=f"""{status(200)}
pm.test("stock is 10", () => pm.expect(pm.response.json().stockQuantity).to.eql(10));"""),
    req("Seller updates product", "PUT", f"{PRODUCTS}/api/products/seller/{{{{sellerId}}}}/product/{{{{productId}}}}", token="sellerToken",
        body={
            "name": "QA Woven Basket", "description": "Updated description.", "price": 45.0,
            "imageUrl": "https://images.unsplash.com/photo-1519710164239-da123dc03ef4",
            "category": "{{rootCategoryName}}", "categoryPath": "{{rootCategoryName}}",
            "city": "Sibiu", "region": "Sibiu", "co2EmissionScore": "Low",
            "shippingMethod": "Carbon Neutral Courier", "isHandmade": True, "lowStockThreshold": 2,
        },
        tests=f"""{status(200)}
{JSON_OK}
pm.test("price updated", () => pm.expect(body.price).to.eql(45));
pm.test("stock unchanged", () => pm.expect(body.stockQuantity).to.eql(10));
pm.collectionVariables.set("productPrice", body.price);"""),
    req("Seller low-stock list", "GET", f"{PRODUCTS}/api/products/seller/{{{{sellerId}}}}/low-stock", token="sellerToken",
        tests=f"""{status(200)}
pm.test("alert type", () => pm.expect(pm.response.json().alertType).to.eql("LOW_STOCK"));"""),
    req("Buyer cannot open seller inventory → 403", "GET", f"{PRODUCTS}/api/products/seller/{{{{sellerId}}}}", token="buyerToken",
        tests=status(403)),
    req("Seller inventory without token → rejected", "GET", f"{PRODUCTS}/api/products/seller/{{{{sellerId}}}}",
        tests=NO_TOKEN_REJECTED),
])

# --------------------------------------------------------------------------- Inventory

inventory = folder("Inventory", "Inventory microservice. NOTE(PLAN 2.1): role checks always fail today, so only rejection paths are asserted.", [
    req("Inventory without token → rejected", "GET", f"{INVENTORY}/api/inventory/seller/{{{{sellerId}}}}",
        tests=NO_TOKEN_REJECTED),
    req("Inventory dashboard without token → rejected", "GET", f"{INVENTORY}/api/inventory/seller/{{{{sellerId}}}}/dashboard",
        tests=NO_TOKEN_REJECTED),
])

# --------------------------------------------------------------------------- Orders – Buyer

def order_body(qty):
    return {
        "buyerId": "{{buyerId}}", "buyerName": "testuser",
        "sellerId": "{{sellerId}}", "sellerName": "seller",
        "items": [{"productId": "{{productId}}", "productName": "QA Woven Basket",
                   "quantity": qty, "price": "{{productPrice}}", "imageUrl": ""}],
        "totalAmount": 0, "shippingAddress": "Str. Testului 1, Sibiu",
    }

def order_body_raw(qty):
    # price must be a number, not a quoted variable
    return json.dumps(order_body(qty), indent=2).replace('"{{productPrice}}"', "{{productPrice}}")

def expect_stock(expected):
    return f"""
pm.sendRequest({{ url: pm.variables.replaceIn("{{{{baseUrlProducts}}}}/api/products/{{{{productId}}}}"), method: "GET" }}, (err, res) => {{
  pm.test("product stock is {expected}", () => {{
    pm.expect(err).to.be.null;
    pm.expect(res.json().stockQuantity).to.eql({expected});
  }});
}});"""

orders_buyer = folder("Orders – Buyer", "Checkout with real-time stock deduction, cancellation with auto-restock, buyer order history.", [
    req("Create order A (qty 2) → stock 10→8", "POST", f"{CORE}/api/orders", token="buyerToken",
        body=order_body_raw(2),
        tests=f"""{status(201)}
{JSON_OK}
pm.test("order is PENDING with an order number", () => {{
  pm.expect(body.status).to.eql("PENDING");
  pm.expect(body.orderNumber).to.match(/^ORD-/);
}});
pm.test("belongs to buyer and seller", () => {{
  pm.expect(body.buyerId).to.eql(pm.collectionVariables.get("buyerId"));
  pm.expect(body.sellerId).to.eql(pm.collectionVariables.get("sellerId"));
}});
pm.collectionVariables.set("orderAId", body.id);
{expect_stock(8)}"""),
    req("Cancel order A → CANCELLED, stock 8→10", "POST", f"{CORE}/api/orders/{{{{orderAId}}}}/cancel", token="buyerToken",
        body={"reason": "Changed my mind"},
        tests=f"""{status(200)}
{JSON_OK}
pm.test("status CANCELLED with reason", () => {{
  pm.expect(body.status).to.eql("CANCELLED");
  pm.expect(body.cancellationReason).to.eql("Changed my mind");
}});
{expect_stock(10)}"""),
    req("Cancel order A again → no double restock", "POST", f"{CORE}/api/orders/{{{{orderAId}}}}/cancel", token="buyerToken",
        body={"reason": "again"},
        tests=f"""{status(200)}
{expect_stock(10)}"""),
    req("Create order B (qty 1) → stock 10→9", "POST", f"{CORE}/api/orders", token="buyerToken",
        body=order_body_raw(1),
        tests=f"""{status(201)}
{JSON_OK}
pm.collectionVariables.set("orderBId", body.id);
pm.collectionVariables.set("orderBNumber", body.orderNumber);
{expect_stock(9)}"""),
    req("Order over available stock → 400", "POST", f"{CORE}/api/orders", token="buyerToken",
        body=order_body_raw(9999),
        tests=f"""// TODO(PLAN 1.9): should become 409 Conflict
{status(400)}
pm.test("explains insufficient stock", () => pm.expect(pm.response.json().error).to.match(/insufficient stock/i));
{expect_stock(9)}"""),
    req("Order without token → 401", "POST", f"{CORE}/api/orders",
        body=order_body_raw(1), tests=status(401)),
    req("Buyer order history", "GET", f"{CORE}/api/orders/buyer/{{{{buyerId}}}}", token="buyerToken",
        tests=f"""{status(200)}
const ids = pm.response.json().map(o => o.id);
pm.test("contains orders A and B", () => {{
  pm.expect(ids).to.include(pm.collectionVariables.get("orderAId"));
  pm.expect(ids).to.include(pm.collectionVariables.get("orderBId"));
}});"""),
    req("Get order B", "GET", f"{CORE}/api/orders/{{{{orderBId}}}}", token="buyerToken",
        tests=f"""{status(200)}
{JSON_OK}
pm.test("one item, qty 1", () => {{
  pm.expect(body.items).to.have.lengthOf(1);
  pm.expect(body.items[0].quantity).to.eql(1);
}});"""),
    req("Get unknown order → 404", "GET", f"{CORE}/api/orders/000000000000000000000000", token="buyerToken",
        tests=status(404)),
])

# --------------------------------------------------------------------------- Orders – Seller

orders_seller = folder("Orders – Seller", "Seller order lifecycle, edit lock, eco label and manual (off-platform) orders.", [
    req("Seller lists own orders", "GET", f"{CORE}/api/seller/orders/seller/{{{{sellerId}}}}", token="sellerToken",
        tests=f"""{status(200)}
{JSON_OK}
pm.test("has orders and count", () => {{
  pm.expect(body.orders).to.be.an("array");
  pm.expect(body.totalOrders).to.eql(body.orders.length);
}});
pm.test("contains order B", () => pm.expect(body.orders.map(o => o.id)).to.include(pm.collectionVariables.get("orderBId")));"""),
    req("Seller edits address while PENDING", "PUT", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/edit", token="sellerToken",
        body={"shippingAddress": "Str. Nouă 2, Sibiu"},
        tests=f"""{status(200)}
pm.test("address updated", () => pm.expect(pm.response.json().order.shippingAddress).to.eql("Str. Nouă 2, Sibiu"));"""),
    req("Seller moves order B to PROCESSING", "PATCH", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/status", token="sellerToken",
        body={"status": "PROCESSING", "estimatedDeliveryDays": "3"},
        tests=f"""{status(200)}
{JSON_OK}
pm.test("status PROCESSING, ETA 3 days", () => {{
  pm.expect(body.order.status).to.eql("PROCESSING");
  pm.expect(body.order.estimatedDeliveryDays).to.eql(3);
}});"""),
    req("Edit locked once PROCESSING → 400", "PUT", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/edit", token="sellerToken",
        body={"shippingAddress": "Too late"},
        tests=f"""{status(400)}
pm.test("explains order is locked", () => pm.expect(pm.response.json().error).to.match(/locked/i));"""),
    req("Invalid status value → 400", "PATCH", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/status", token="sellerToken",
        body={"status": "TELEPORTED"}, tests=status(400)),
    req("Generate eco shipping label", "POST", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/eco-label", token="sellerToken",
        tests=f"""{status(200)}
pm.test("label returned", () => pm.expect(pm.response.json().label).to.be.a("string").and.not.empty);"""),
    req("Seller moves order B to SHIPPED", "PATCH", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/status", token="sellerToken",
        body={"status": "SHIPPED"},
        tests=f"""{status(200)}
pm.test("status SHIPPED", () => pm.expect(pm.response.json().order.status).to.eql("SHIPPED"));"""),
    req("Buyer cannot use seller order endpoints → 403", "PATCH", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/status", token="buyerToken",
        body={"status": "DELIVERED"}, tests=status(403)),
    req("Seller creates manual order (qty 1) → stock 9→8", "POST", f"{CORE}/api/seller/orders/manual", token="sellerToken",
        body=json.dumps({
            "sellerId": "{{sellerId}}", "sellerName": "seller", "buyerName": "Craft fair visitor",
            "shippingAddress": "Picked up at stand", "paymentMethod": "CASH", "source": "CRAFT_FAIR",
            "items": [{"productId": "{{productId}}", "productName": "QA Woven Basket", "quantity": 1, "price": "{{productPrice}}"}],
        }, indent=2).replace('"{{productPrice}}"', "{{productPrice}}"),
        tests=f"""{status(201)}
{JSON_OK}
pm.test("manual order is COMPLETED with MAN- number", () => {{
  pm.expect(body.order.status).to.eql("COMPLETED");
  pm.expect(body.orderNumber).to.match(/^MAN-/);
}});
{expect_stock(8)}"""),
    req("Manual order missing fields → 400", "POST", f"{CORE}/api/seller/orders/manual", token="sellerToken",
        body={"sellerId": "{{sellerId}}"}, tests=status(400)),
])

# --------------------------------------------------------------------------- Admin

admin = folder("Admin", "User governance, order intervention and audit trail.", [
    req("List users", "GET", f"{CORE}/api/admin/users", token="adminToken",
        tests=f"""{status(200)}
const users = pm.response.json();
pm.test("contains seeded users", () => pm.expect(users.map(u => u.username)).to.include.members(["admin", "seller", "testuser"]));
pm.test("CRM shape", () => pm.expect(users[0]).to.include.keys("id", "username", "email", "roles", "strikes", "accountStatus"));"""),
    req("Buyer cannot list users → 403", "GET", f"{CORE}/api/admin/users", token="buyerToken",
        tests=status(403)),
    req("Get user by id", "GET", f"{CORE}/api/admin/users/{{{{newUserId}}}}", token="adminToken",
        tests=f"""{status(200)}
pm.test("is the new user", () => pm.expect(pm.response.json().username).to.eql(pm.collectionVariables.get("newUsername")));"""),
    req("Get unknown user → 404", "GET", f"{CORE}/api/admin/users/000000000000000000000000", token="adminToken",
        tests=status(404)),
    req("Give new user a strike", "POST", f"{CORE}/api/admin/users/{{{{newUserId}}}}/penalty", token="adminToken",
        body={"action": "STRIKE"},
        tests=f"""{status(200)}
pm.test("strikes = 1", () => pm.expect(pm.response.json().strikes).to.eql(1));"""),
    req("Suspend new user for 7 days", "POST", f"{CORE}/api/admin/users/{{{{newUserId}}}}/penalty", token="adminToken",
        body={"action": "STATUS", "newStatus": "SUSPENDED", "suspendDays": 7},
        tests=f"""{status(200)}
{JSON_OK}
pm.test("SUSPENDED with an end date", () => {{
  pm.expect(body.accountStatus).to.eql("SUSPENDED");
  pm.expect(body.suspendedUntil).to.not.be.null;
}});"""),
    req("Suspended user cannot sign in → 401", "POST", f"{CORE}/api/auth/signin",
        body={"username": "{{newUsername}}", "password": "secret123"}, tests=status(401)),
    req("Reactivate new user", "POST", f"{CORE}/api/admin/users/{{{{newUserId}}}}/penalty", token="adminToken",
        body={"action": "STATUS", "newStatus": "ACTIVE"},
        tests=f"""{status(200)}
pm.test("ACTIVE again", () => pm.expect(pm.response.json().accountStatus).to.eql("ACTIVE"));"""),
    req("Invalid penalty action → 400", "POST", f"{CORE}/api/admin/users/{{{{newUserId}}}}/penalty", token="adminToken",
        body={"action": "EXILE"}, tests=status(400)),
    req("List all orders", "GET", f"{CORE}/api/admin/orders", token="adminToken",
        tests=f"""{status(200)}
pm.test("contains order B", () => pm.expect(pm.response.json().map(o => o.id)).to.include(pm.collectionVariables.get("orderBId")));"""),
    req("Get order by number", "GET", f"{CORE}/api/admin/orders/number/{{{{orderBNumber}}}}", token="adminToken",
        tests=f"""{status(200)}
pm.test("is order B", () => pm.expect(pm.response.json().id).to.eql(pm.collectionVariables.get("orderBId")));"""),
    req("Search orders by buyer name", "GET", f"{CORE}/api/admin/orders/search?query=testuser", token="adminToken",
        tests=f"""{status(200)}
pm.test("all results match", () => pm.response.json().forEach(o =>
  pm.expect((o.buyerName + o.sellerName + o.orderNumber).toLowerCase()).to.include("testuser")));"""),
    req("Orders by status SHIPPED", "GET", f"{CORE}/api/admin/orders/status/SHIPPED", token="adminToken",
        tests=f"""{status(200)}
pm.test("all SHIPPED", () => pm.response.json().forEach(o => pm.expect(o.status).to.eql("SHIPPED")));"""),
    req("Intervention: update tracking number", "POST", f"{CORE}/api/admin/orders/{{{{orderBId}}}}/intervene", token="adminToken",
        body={"action": "UPDATE_TRACKING", "trackingNumber": "TRK-QA-001", "adminNotes": "Carrier mix-up fixed"},
        tests=f"""{status(200)}
pm.test("tracking updated", () => pm.expect(pm.response.json().trackingNumber).to.eql("TRK-QA-001"));"""),
    req("Audit trail records the intervention", "GET", f"{CORE}/api/admin/orders/audit", token="adminToken",
        tests=f"""{status(200)}
const logs = pm.response.json();
pm.test("has an ORDER_INTERVENTION entry for order B", () => pm.expect(
  logs.some(l => l.targetId === pm.collectionVariables.get("orderBId") && l.actionType === "ORDER_INTERVENTION")).to.be.true);"""),
    req("Seller cannot intervene → 403", "POST", f"{CORE}/api/admin/orders/{{{{orderBId}}}}/intervene", token="sellerToken",
        body={"action": "FORCE_REFUND"}, tests=status(403)),
])

# --------------------------------------------------------------------------- Chat

chat = folder("Chat", "Pre-purchase messaging between buyer and seller.", [
    req("Buyer messages seller about product", "POST", f"{CORE}/api/chat/send", token="buyerToken",
        body={"senderId": "{{buyerId}}", "receiverId": "{{sellerId}}", "productId": "{{productId}}",
              "content": "Hi! Could you make this basket in blue?"},
        tests=f"""{status(201)}
{JSON_OK}
pm.test("stored unread with timestamp", () => {{
  pm.expect(body.id).to.be.a("string");
  pm.expect(body.read).to.eql(false);
  pm.expect(body.timestamp).to.be.a("string");
}});
pm.collectionVariables.set("chatMessageId", body.id);"""),
    req("Seller sees unread message", "GET", f"{CORE}/api/chat/unread/{{{{sellerId}}}}", token="sellerToken",
        tests=f"""{status(200)}
pm.test("contains the message", () => pm.expect(pm.response.json().map(m => m.id)).to.include(pm.collectionVariables.get("chatMessageId")));"""),
    req("Seller opens conversation (marks read)", "GET", f"{CORE}/api/chat/conversation?user1={{{{sellerId}}}}&user2={{{{buyerId}}}}", token="sellerToken",
        tests=f"""{status(200)}
const msgs = pm.response.json();
pm.test("chronological order", () => {{
  for (let i = 1; i < msgs.length; i++) pm.expect(msgs[i].timestamp >= msgs[i - 1].timestamp).to.be.true;
}});
pm.test("message now read", () => pm.expect(msgs.find(m => m.id === pm.collectionVariables.get("chatMessageId")).read).to.be.true);"""),
    req("Seller replies", "POST", f"{CORE}/api/chat/send", token="sellerToken",
        body={"senderId": "{{sellerId}}", "receiverId": "{{buyerId}}", "productId": "{{productId}}",
              "content": "Sure, blue is possible!"},
        tests=status(201)),
    req("Buyer conversation list", "GET", f"{CORE}/api/chat/conversations/{{{{buyerId}}}}", token="buyerToken",
        tests=f"""{status(200)}
pm.test("one entry per partner incl. seller", () => {{
  const partners = pm.response.json().map(m => m.senderId === pm.collectionVariables.get("buyerId") ? m.receiverId : m.senderId);
  pm.expect(partners).to.include(pm.collectionVariables.get("sellerId"));
  pm.expect(new Set(partners).size).to.eql(partners.length);
}});"""),
    req("Chat without token → 401", "GET", f"{CORE}/api/chat/unread/{{{{sellerId}}}}", tests=status(401)),
])

# --------------------------------------------------------------------------- Reviews

reviews = folder("Reviews", "Product reviews (products service) and seller reviews (core).", [
    req("Buyer reviews product", "POST", f"{PRODUCTS}/api/reviews", token="buyerToken",
        body={"productId": "{{productId}}", "userId": "{{buyerId}}", "username": "testuser",
              "rating": 5, "comment": "Beautiful craftsmanship.", "photoUrls": []},
        tests=f"""{status(201)}
pm.test("rating stored", () => pm.expect(pm.response.json().rating).to.eql(5));"""),
    req("Product reviews with average", "GET", f"{PRODUCTS}/api/reviews/product/{{{{productId}}}}",
        tests=f"""{status(200)}
{JSON_OK}
pm.test("has review and average", () => {{
  pm.expect(body.totalReviews).to.be.at.least(1);
  pm.expect(body.averageRating).to.be.within(1, 5);
}});"""),
    req("Buyer marks order B item reviewed", "POST", f"{CORE}/api/orders/{{{{orderBId}}}}/mark-reviewed", token="buyerToken",
        body='["{{productId}}"]',
        tests=f"""{status(200)}
pm.sendRequest({{ url: pm.variables.replaceIn("{{{{baseUrlCore}}}}/api/orders/{{{{orderBId}}}}"), method: "GET",
  header: {{ Authorization: "Bearer " + pm.collectionVariables.get("buyerToken") }} }}, (err, res) => {{
  pm.test("item flagged reviewed", () => pm.expect(res.json().items[0].reviewed).to.be.true);
}});"""),
    req("Buyer reviews seller", "POST", f"{CORE}/api/public/users/seller/{{{{sellerId}}}}/reviews", token="buyerToken",
        body={"buyerId": "{{buyerId}}", "buyerName": "testuser", "overallRating": 5,
              "communicationRating": 5, "shippingRating": 4, "comment": "Fast replies."},
        tests=f"""{status(200)}
pm.test("bound to seller", () => pm.expect(pm.response.json().sellerId).to.eql(pm.collectionVariables.get("sellerId")));"""),
    req("Seller reviews list", "GET", f"{CORE}/api/public/users/seller/{{{{sellerId}}}}/reviews",
        tests=f"""{status(200)}
pm.test("at least one review", () => pm.expect(pm.response.json()).to.be.an("array").that.is.not.empty);"""),
])

# --------------------------------------------------------------------------- Returns

returns = folder("Returns", "Return requests on delivered orders.", [
    req("Buyer confirms receipt of order B (precondition)", "POST", f"{CORE}/api/orders/{{{{orderBId}}}}/confirm-receipt", token="buyerToken",
        tests=f"""{status(200)}
pm.test("DELIVERED", () => pm.expect(pm.response.json().status).to.eql("DELIVERED"));"""),
    req("Buyer requests return for order B", "POST", f"{CORE}/api/returns", token="buyerToken",
        body=json.dumps({
            "orderId": "{{orderBId}}", "orderNumber": "{{orderBNumber}}", "buyerId": "{{buyerId}}",
            "sellerId": "{{sellerId}}", "sellerName": "seller", "reason": "Colour differs from photo",
            "status": "PICKED_UP", "totalAmount": "{{productPrice}}",
            "items": [{"productId": "{{productId}}", "productName": "QA Woven Basket", "quantity": 1, "price": "{{productPrice}}"}],
        }, indent=2).replace('"{{productPrice}}"', "{{productPrice}}"),
        tests=f"""{status(200)}
{JSON_OK}
pm.test("return stored for order B", () => pm.expect(body.orderId).to.eql(pm.collectionVariables.get("orderBId")));
pm.collectionVariables.set("returnId", body.id);"""),
    req("Duplicate return for same item → 400", "POST", f"{CORE}/api/returns", token="buyerToken",
        body={"orderId": "{{orderBId}}", "buyerId": "{{buyerId}}", "sellerId": "{{sellerId}}",
              "items": [{"productId": "{{productId}}", "quantity": 1}]},
        tests=f"""{status(400)}
pm.test("explains duplicate", () => pm.expect(pm.response.json().message).to.match(/already requested/i));"""),
    req("Return for unknown order → 400", "POST", f"{CORE}/api/returns", token="buyerToken",
        body={"orderId": "000000000000000000000000", "items": []},
        tests=status(400)),
    req("Buyer return list", "GET", f"{CORE}/api/returns/buyer/{{{{buyerId}}}}", token="buyerToken",
        tests=f"""{status(200)}
pm.test("contains the return", () => pm.expect(pm.response.json().map(r => r.id)).to.include(pm.collectionVariables.get("returnId")));"""),
    req("Seller return list", "GET", f"{CORE}/api/returns/seller/{{{{sellerId}}}}", token="sellerToken",
        tests=f"""{status(200)}
pm.test("contains the return", () => pm.expect(pm.response.json().map(r => r.id)).to.include(pm.collectionVariables.get("returnId")));"""),
    req("Seller updates return status", "PATCH", f"{CORE}/api/returns/{{{{returnId}}}}/status", token="sellerToken",
        body={"status": "ARRIVED"},
        tests=f"""{status(200)}
pm.test("status ARRIVED", () => pm.expect(pm.response.json().status).to.eql("ARRIVED"));"""),
    req("Buyer cannot update return status → 403", "PATCH", f"{CORE}/api/returns/{{{{returnId}}}}/status", token="buyerToken",
        body={"status": "REFUNDED"}, tests=status(403)),
    req("Admin lists all returns", "GET", f"{CORE}/api/returns/all", token="adminToken",
        tests=f"""{status(200)}
pm.test("is an array", () => pm.expect(pm.response.json()).to.be.an("array").that.is.not.empty);"""),
])

# --------------------------------------------------------------------------- Reports

reports = folder("Reports", "Flagged content queue; admin may read chat logs only through a CHAT report.", [
    req("Buyer reports chat", "POST", f"{CORE}/api/reports", token="buyerToken",
        body={"reporterId": "{{buyerId}}", "reporterName": "testuser", "targetType": "CHAT",
              "targetId": "{{buyerId}}:{{sellerId}}", "targetName": "seller",
              "reason": "Testing moderation flow", "status": "PENDING"},
        tests=f"""{status(200)}
pm.collectionVariables.set("reportId", pm.response.json().id);"""),
    req("Admin flagged-content queue", "GET", f"{CORE}/api/reports/all", token="adminToken",
        tests=f"""{status(200)}
pm.test("contains the report", () => pm.expect(pm.response.json().map(r => r.id)).to.include(pm.collectionVariables.get("reportId")));"""),
    req("Admin reads reported chat log", "GET", f"{CORE}/api/reports/chat-log/{{{{reportId}}}}", token="adminToken",
        tests=f"""{status(200)}
const msgs = pm.response.json();
pm.test("only messages between the two participants", () => {{
  const ids = [pm.collectionVariables.get("buyerId"), pm.collectionVariables.get("sellerId")];
  pm.expect(msgs).to.not.be.empty;
  msgs.forEach(m => {{ pm.expect(ids).to.include(m.senderId); pm.expect(ids).to.include(m.receiverId); }});
}});"""),
    req("Admin resolves report", "PATCH", f"{CORE}/api/reports/{{{{reportId}}}}/status", token="adminToken",
        body={"status": "RESOLVED"},
        tests=f"""{status(200)}
pm.test("RESOLVED", () => pm.expect(pm.response.json().status).to.eql("RESOLVED"));"""),
    req("Buyer cannot read report queue → 403", "GET", f"{CORE}/api/reports/all", token="buyerToken",
        tests=status(403)),
    req("Buyer cannot read chat logs → 403", "GET", f"{CORE}/api/reports/chat-log/{{{{reportId}}}}", token="buyerToken",
        tests=status(403)),
])

# --------------------------------------------------------------------------- Cleanup

cleanup = folder("Cleanup", "Removes the test product so repeated runs don't pile up catalog entries.", [
    req("Seller deletes test product", "DELETE", f"{PRODUCTS}/api/products/seller/{{{{sellerId}}}}/product/{{{{productId}}}}", token="sellerToken",
        tests=f"""{status(200)}
pm.sendRequest({{ url: pm.variables.replaceIn("{{{{baseUrlProducts}}}}/api/products/{{{{productId}}}}"), method: "GET" }}, (err, res) => {{
  pm.test("product is gone", () => pm.expect(res.code).to.eql(404));
}});"""),
])

# --------------------------------------------------------------------------- collections

VARIABLES = [
    "adminToken", "adminId", "sellerToken", "sellerId", "buyerToken", "buyerId",
    "newUsername", "newEmail", "newUserToken", "newUserId", "garbageToken",
    "rootCategoryId", "rootCategoryName", "productId", "productPrice",
    "orderAId", "orderBId", "orderBNumber", "chatMessageId", "returnId", "reportId",
]

def collection(name, description, items, pid):
    return {
        "info": {
            "_postman_id": pid,
            "name": name,
            "description": description,
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "item": items,
        "variable": [{"key": k, "value": ""} for k in VARIABLES],
    }

main = collection(
    "Wovely API",
    "End-to-end API scenario for the Wovely microservices (core :8081, products :8082, inventory :8083).\n"
    "Run the whole collection in order with an environment from postman/. Generated by "
    "scripts/postman/build_collection.py — edit that script, not this file.",
    [auth, products, inventory, orders_buyer, orders_seller, admin, chat, reviews, returns, reports, cleanup],
    "6f1e0a52-3c1b-4d47-9a8e-0b7a1c2d3e01",
)

# --------------------------------------------------------------------------- seed collection
# Replaces the former root scripts (inject_reviews*.ps1, p1-3.json, mock_products.json, ...).

SEED_PRODUCTS = [
    {"name": "Handcrafted Ceramic Mug", "description": "Solar-powered kiln fired mug.", "price": 24.5,
     "imageUrl": "https://images.unsplash.com/photo-1614806687036-71d37803e7d6", "category": "Home & Living",
     "categoryPath": "Home & Living > Ceramics", "city": "Cluj-Napoca", "region": "Transylvania",
     "co2EmissionScore": "Low", "shippingMethod": "Carbon Neutral Courier", "stockQuantity": 15},
    {"name": "Woven Easter Basket", "description": "Purple hand-woven basket.", "price": 105.0,
     "imageUrl": "https://free-images.com/lg/2532/easter_basket_purple_basket.jpg", "category": "Art & Collectibles",
     "categoryPath": "Art & Collectibles", "city": "Sibiu", "region": "Sibiu",
     "co2EmissionScore": "Low", "shippingMethod": "Standard", "stockQuantity": 9},
    {"name": "Hand-thrown Vase", "description": "Stoneware vase with natural glaze.", "price": 68.0,
     "imageUrl": "https://images.unsplash.com/photo-1578500494198-246f612d3b3d", "category": "Home & Living",
     "categoryPath": "Home & Living > Ceramics", "city": "Brașov", "region": "Brașov",
     "co2EmissionScore": "Medium", "shippingMethod": "Standard", "stockQuantity": 4},
]
SEED_REVIEWS = [
    ("Alice S.", 5, "Absolutely stunning! The quality is incredible."),
    ("Bob G.", 4, "Very durable and looks great."),
    ("Diana M.", 3, "It's okay, but the packaging was a bit thin."),
]

seed_items = [signin("seller", "seller", "seller123", "sellerToken", "sellerId"),
              signin("admin", "admin", "admin123", "adminToken", "adminId"),
              signin("buyer", "testuser", "user123", "buyerToken", "buyerId")]
for i, p in enumerate(SEED_PRODUCTS, 1):
    body = dict(p, sellerId="{{sellerId}}", isHandmade=True, lowStockThreshold=5)
    seed_items.append(req(f"Create product {i}: {p['name']}", "POST", f"{PRODUCTS}/api/products", token="sellerToken",
        body=body, tests=f"""{status(201)}
pm.collectionVariables.set("seedProduct{i}", pm.response.json().id);"""))
    seed_items.append(req(f"Approve product {i}", "PUT", f"{PRODUCTS}/api/products/admin/{{{{seedProduct{i}}}}}/status",
        token="adminToken", body={"status": "APPROVED"}, tests=status(200)))
for j, (user, rating, comment) in enumerate(SEED_REVIEWS):
    pid = "{{seedProduct" + str(1 + j % 2) + "}}"
    seed_items.append(req(f"Review by {user}", "POST", f"{PRODUCTS}/api/reviews", token="buyerToken",
        body={"productId": pid, "userId": "{{buyerId}}", "username": user, "rating": rating,
              "comment": comment, "photoUrls": []}, tests=status(201)))

seed = collection(
    "Wovely Seed Data",
    "Populates a fresh stack with demo products and reviews. Not part of the test run. "
    "Generated by scripts/postman/build_collection.py.",
    seed_items,
    "6f1e0a52-3c1b-4d47-9a8e-0b7a1c2d3e02",
)
seed["variable"] += [{"key": f"seedProduct{i}", "value": ""} for i in range(1, len(SEED_PRODUCTS) + 1)]

ENVS = {
    "local": ("http://localhost:8081", "http://localhost:8082", "http://localhost:8083"),
    "docker": ("http://wovely-backend:8081", "http://products:8082", "http://inventory:8083"),
}

def env(name, urls):
    return {
        "id": str(uuid.uuid5(uuid.NAMESPACE_URL, "wovely-env-" + name)),
        "name": f"Wovely – {name}",
        "values": [
            {"key": "baseUrlCore", "value": urls[0], "type": "default", "enabled": True},
            {"key": "baseUrlProducts", "value": urls[1], "type": "default", "enabled": True},
            {"key": "baseUrlInventory", "value": urls[2], "type": "default", "enabled": True},
        ],
        "_postman_variable_scope": "environment",
    }

os.makedirs(OUT_DIR, exist_ok=True)
def dump(obj, fname):
    with open(os.path.join(OUT_DIR, fname), "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")

dump(main, "Wovely.postman_collection.json")
dump(seed, "Wovely.seed.postman_collection.json")
for n, u in ENVS.items():
    dump(env(n, u), f"{n}.postman_environment.json")

count = sum(len(f["item"]) for f in main["item"])
print(f"main collection: {len(main['item'])} folders, {count} requests; seed: {len(seed_items)} requests")
