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
GATEWAY = "{{baseUrlGateway}}"
JSON_OK = 'const body = pm.response.json();'
NO_TOKEN_REJECTED = status(401)

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
pm.test("returns a refresh token and short TTL (PLAN 2.9)", () => {{
  pm.expect(body.refreshToken).to.be.a("string").and.not.empty;
  pm.expect(body.expiresInMs).to.be.at.most(3600000);
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
    req("Second session for refresh tests", "POST", f"{CORE}/api/auth/signin",
        body={"username": "{{newUsername}}", "password": "secret123"},
        tests=f"""{status(200)}
pm.collectionVariables.set("refreshA", pm.response.json().refreshToken);"""),
    req("Refresh → new access + rotated refresh (PLAN 2.9)", "POST", f"{CORE}/api/auth/refresh",
        body={"refreshToken": "{{refreshA}}"},
        tests=f"""{status(200)}
{JSON_OK}
pm.test("new tokens", () => {{
  pm.expect(body.accessToken).to.be.a("string").and.not.empty;
  pm.expect(body.refreshToken).to.not.eql(pm.collectionVariables.get("refreshA"));
  pm.expect(body.username).to.eql(pm.collectionVariables.get("newUsername"));
}});
pm.collectionVariables.set("refreshB", body.refreshToken);"""),
    req("Reusing a rotated refresh token → 401 (theft detection)", "POST", f"{CORE}/api/auth/refresh",
        body={"refreshToken": "{{refreshA}}"}, tests=status(401)),
    req("…and it revoked the whole family → 401", "POST", f"{CORE}/api/auth/refresh",
        body={"refreshToken": "{{refreshB}}"}, tests=status(401)),
    req("Sign out is idempotent", "POST", f"{CORE}/api/auth/signout",
        body={"refreshToken": "{{refreshB}}"}, tests=status(200)),
    req("Refresh with garbage → 401", "POST", f"{CORE}/api/auth/refresh",
        body={"refreshToken": "nope"}, tests=status(401)),
    req("Sign up with taken username → 400", "POST", f"{CORE}/api/auth/signup",
        body={"username": "seller", "email": "other@wovely.test", "password": "secret123"},
        tests=f"""{status(400)}
pm.test("explains username is taken", () => pm.expect(pm.response.json().message).to.match(/username is already taken/i));"""),
    req("Sign up with invalid email/short password → 400", "POST", f"{CORE}/api/auth/signup",
        body={"username": "ab", "email": "not-an-email", "password": "123"},
        tests=status(400)),
    req("Sign up as admin is rejected → 400 (PLAN 1.1)", "POST", f"{CORE}/api/auth/signup",
        body={"username": "wannabe_admin", "email": "wannabe@wovely.test", "password": "secret123", "roles": ["admin"]},
        tests=f"""{status(400)}
pm.test("explains admin cannot be self-assigned", () => pm.expect(pm.response.json().message).to.match(/cannot be self-assigned/i));"""),
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
    req("Buyer cannot read seller's private profile → 403 (PLAN 1.4)", "GET", f"{CORE}/api/user/profile/{{{{sellerId}}}}", token="buyerToken",
        tests=status(403)),
    req("Buyer cannot edit seller's profile → 403 (PLAN 1.4)", "PUT", f"{CORE}/api/user/profile/{{{{sellerId}}}}", token="buyerToken",
        body={"makerStory": "hacked"}, tests=status(403)),
    req("Public seller profile", "GET", f"{CORE}/api/public/users/seller/{{{{sellerId}}}}",
        tests=f"""{status(200)}
{JSON_OK}
pm.test("shows updated city", () => pm.expect(body.city).to.eql("Sibiu"));
pm.test("does not expose email", () => pm.expect(body).to.not.have.property("email"));"""),
])

def expect_stock(expected):
    """Inventory owns stock (PLAN 4.2): it must show the value at once; the catalog's read model is fed
    by RabbitMQ events, so poll it (up to ~5 s) until it catches up."""
    return f"""
pm.sendRequest({{ url: pm.variables.replaceIn("{{{{baseUrlInventory}}}}/api/inventory/seller/{{{{sellerId}}}}/product/{{{{productId}}}}"),
  method: "GET", header: {{ Authorization: "Bearer " + pm.collectionVariables.get("sellerToken") }} }}, (err, res) => {{
  pm.test("inventory (stock owner) shows {expected}", () => {{
    pm.expect(err).to.be.null;
    pm.expect(res.json().stockQuantity).to.eql({expected});
  }});
}});
(function pollCatalog(left) {{
  pm.sendRequest({{ url: pm.variables.replaceIn("{{{{baseUrlProducts}}}}/api/products/{{{{productId}}}}"), method: "GET" }}, (err, res) => {{
    const seen = err ? undefined : res.json().stockQuantity;
    if (seen === {expected} || left === 0) {{
      pm.test("catalog read model caught up to {expected} (event-driven)", () => pm.expect(seen).to.eql({expected}));
    }} else {{
      setTimeout(() => pollCatalog(left - 1), 250);
    }}
  }});
}})(20);"""


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
    req("Anonymous cannot create product → 401 (PLAN 1.2)", "POST", f"{PRODUCTS}/api/products",
        body={"name": "Spam", "price": 1, "sellerId": "{{sellerId}}"}, tests=status(401)),
    req("Buyer cannot create product → 403 (PLAN 1.2)", "POST", f"{PRODUCTS}/api/products", token="buyerToken",
        body={"name": "Spam", "price": 1}, tests=status(403)),
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
pm.test("new listings await moderation (PENDING)", () => pm.expect(body.status).to.eql("PENDING"));
pm.test("carbon-neutral courier → LOW delivery emission (PLAN 3.5)", () => pm.expect(body.deliveryEmission).to.eql("LOW"));
pm.collectionVariables.set("productId", body.id);
pm.collectionVariables.set("productPrice", body.price);"""),
    req("Pending product is not in the public catalog", "GET", f"{PRODUCTS}/api/products",
        tests=f"""{status(200)}
pm.test("not listed before approval", () => pm.expect(pm.response.json().map(p => p.id)).to.not.include(pm.collectionVariables.get("productId")));"""),
    req("Admin approves product", "PUT", f"{PRODUCTS}/api/products/admin/{{{{productId}}}}/status", token="adminToken",
        body={"status": "APPROVED"},
        tests=f"""{status(200)}
pm.test("status is APPROVED", () => pm.expect(pm.response.json().status).to.eql("APPROVED"));"""),
    req("Invalid moderation status → 400", "PUT", f"{PRODUCTS}/api/products/admin/{{{{productId}}}}/status", token="adminToken",
        body={"status": "MAYBE"}, tests=status(400)),
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
  pm.expect(p.deliveryEmission).to.be.oneOf(["ZERO", "LOW"]);
  pm.expect(p.price).to.be.within(10, 100);
  pm.expect(p.city.toLowerCase()).to.include("sib");
}}));
pm.test("new product matches", () => pm.expect(list.map(p => p.id)).to.include(pm.collectionVariables.get("productId")));"""),
    req("Catalog pagination (PLAN 2.10)", "GET", f"{PRODUCTS}/api/products?page=0&size=1",
        tests=f"""{status(200)}
pm.test("at most one item", () => pm.expect(pm.response.json().length).to.be.at.most(1));
pm.test("X-Total-Count header", () => pm.expect(Number(pm.response.headers.get("X-Total-Count"))).to.be.at.least(1));"""),
    req("Catalog filter input is regex-quoted", "GET", f"{PRODUCTS}/api/products?city=.*",
        tests=f"""{status(200)}
pm.test("'.*' matches literally, not everything", () => pm.response.json().forEach(p => pm.expect(p.city).to.include(".*")));"""),
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
    req("Seller offers local pickup; claimed level is ignored (PLAN 3.5)", "PUT", f"{PRODUCTS}/api/products/seller/{{{{sellerId}}}}/product/{{{{productId}}}}", token="sellerToken",
        body={
            "name": "QA Woven Basket", "description": "Updated description.", "price": 45.0,
            "imageUrl": "https://images.unsplash.com/photo-1519710164239-da123dc03ef4",
            "category": "{{rootCategoryName}}", "categoryPath": "{{rootCategoryName}}",
            "city": "Sibiu", "region": "Sibiu", "co2EmissionScore": "High",
            "shippingMethod": "Express", "localPickup": True, "deliveryEmission": "STANDARD",
            "isHandmade": True, "lowStockThreshold": 2,
        },
        tests=f"""{status(200)}
{JSON_OK}
pm.test("local pickup → ZERO", () => pm.expect(body.deliveryEmission).to.eql("ZERO"));
pm.test("localPickup stored", () => pm.expect(body.localPickup).to.be.true);"""),
    req("Catalog: zero-emission only (PLAN 3.5)", "GET", f"{PRODUCTS}/api/products?deliveryEmission=ZERO",
        tests=f"""{status(200)}
const list = pm.response.json();
pm.test("every result is ZERO", () => list.forEach(p => pm.expect(p.deliveryEmission).to.eql("ZERO")));
pm.test("contains the pickup product", () => pm.expect(list.map(p => p.id)).to.include(pm.collectionVariables.get("productId")));"""),
    req("Catalog: eco-friendly includes zero-emission", "GET", f"{PRODUCTS}/api/products?deliveryEmission=LOW",
        tests=f"""{status(200)}
const list = pm.response.json();
pm.test("ZERO or LOW only", () => list.forEach(p => pm.expect(p.deliveryEmission).to.be.oneOf(["ZERO", "LOW"])));
pm.test("contains the pickup product", () => pm.expect(list.map(p => p.id)).to.include(pm.collectionVariables.get("productId")));"""),
    req("Catalog: local pickup filter", "GET", f"{PRODUCTS}/api/products?localPickup=true",
        tests=f"""{status(200)}
const list = pm.response.json();
pm.test("every result offers pickup", () => list.forEach(p => pm.expect(p.localPickup).to.be.true));
pm.test("contains the pickup product", () => pm.expect(list.map(p => p.id)).to.include(pm.collectionVariables.get("productId")));"""),
    req("Catalog: unknown emission level → 400", "GET", f"{PRODUCTS}/api/products?deliveryEmission=NEGATIVE",
        tests=status(400)),
    req("Seller low-stock list", "GET", f"{PRODUCTS}/api/products/seller/{{{{sellerId}}}}/low-stock", token="sellerToken",
        tests=f"""{status(200)}
pm.test("alert type", () => pm.expect(pm.response.json().alertType).to.eql("LOW_STOCK"));"""),
    req("Buyer cannot open seller inventory → 403", "GET", f"{PRODUCTS}/api/products/seller/{{{{sellerId}}}}", token="buyerToken",
        tests=status(403)),
    req("Seller inventory without token → 401", "GET", f"{PRODUCTS}/api/products/seller/{{{{sellerId}}}}",
        tests=NO_TOKEN_REJECTED),
    req("Buyer cannot restock seller's product → 403 (PLAN 1.4)", "POST", f"{PRODUCTS}/api/products/seller/{{{{sellerId}}}}/product/{{{{productId}}}}/restock", token="buyerToken",
        body={"quantity": 1000}, tests=status(403)),
    req("Old public reduce-stock is closed → 401 (PLAN 1.3)", "POST", f"{PRODUCTS}/api/products/seller/{{{{sellerId}}}}/product/{{{{productId}}}}/reduce-stock",
        body={"quantity": 10}, tests=status(401)),
    req("Products has no stock API for other services any more (PLAN 4.2)", "POST", f"{PRODUCTS}/internal/products/seller/{{{{sellerId}}}}/product/{{{{productId}}}}/reduce-stock",
        body={"quantity": 10}, tests=status(401)),
    req("Inventory stock API with user JWT → 403 (PLAN 1.3 / 4.2)", "POST", f"{INVENTORY}/internal/inventory/stock/{{{{productId}}}}/reserve", token="sellerToken",
        body={"quantity": 10}, tests=f"""{status(403)}
{expect_stock(10)}"""),
    req("Buyer cannot create categories → 403", "POST", f"{PRODUCTS}/api/categories", token="buyerToken",
        body={"name": "Spam", "level": 0}, tests=status(403)),
])

# --------------------------------------------------------------------------- Inventory

inventory = folder("Inventory", "Inventory microservice: seller-owned views; sync/stock only via /internal with the service key.", [
    req("Seller reads own inventory (PLAN 2.1)", "GET", f"{INVENTORY}/api/inventory/seller/{{{{sellerId}}}}", token="sellerToken",
        tests=f"""{status(200)}
pm.test("has items and counters", () => pm.expect(pm.response.json()).to.include.keys("items", "totalItems"));"""),
    req("Buyer cannot read seller inventory → 403 (PLAN 1.4)", "GET", f"{INVENTORY}/api/inventory/seller/{{{{sellerId}}}}", token="buyerToken",
        tests=status(403)),
    req("Old public sync endpoint is closed → 401 (PLAN 1.3)", "PUT", f"{INVENTORY}/api/inventory/seller/{{{{sellerId}}}}/product/{{{{productId}}}}",
        body={"stockQuantity": 999}, tests=status(401)),
    req("Inventory stock API without key → 401 (PLAN 1.3 / 4.2)", "POST", f"{INVENTORY}/internal/inventory/stock/{{{{productId}}}}/reserve",
        body={"quantity": 1}, tests=status(401)),
    req("Inventory without token → 401", "GET", f"{INVENTORY}/api/inventory/seller/{{{{sellerId}}}}",
        tests=NO_TOKEN_REJECTED),
    req("Inventory dashboard without token → 401", "GET", f"{INVENTORY}/api/inventory/seller/{{{{sellerId}}}}/dashboard",
        tests=NO_TOKEN_REJECTED),
])


# --------------------------------------------------------------------------- Gateway (PLAN 4.1)

ONE_CORRELATION_ID = """pm.test("exactly one X-Correlation-Id", () => {
  const ids = pm.response.headers.all().filter(h => h.key.toLowerCase() === "x-correlation-id");
  pm.expect(ids).to.have.lengthOf(1);
});"""

gateway = folder("Gateway", "Single entry point (Spring Cloud Gateway): routing, edge JWT check, /internal blocked, "
                 "correlation ids, Redis rate limiting. The browser reaches it through nginx on :4200.", [
    req("Catalog is routed to products", "GET", f"{GATEWAY}/api/products?page=0&size=1",
        tests=f"""{status(200)}
pm.test("array of products", () => pm.expect(pm.response.json()).to.be.an("array"));
pm.test("rate-limit headers present", () => pm.expect(pm.response.headers.has("X-RateLimit-Remaining")).to.be.true);
{ONE_CORRELATION_ID}"""),
    req("Wallet is routed to core (token passes the edge)", "GET", f"{GATEWAY}/api/wallet", token="buyerToken",
        tests=f"""{status(200)}
pm.test("wallet shape", () => pm.expect(pm.response.json()).to.have.property("balanceCents"));"""),
    req("Inventory is routed to inventory", "GET", f"{GATEWAY}/api/inventory/seller/{{{{sellerId}}}}", token="sellerToken",
        tests=status(200)),
    req("Invalid token stops at the edge → 401", "GET", f"{GATEWAY}/api/wallet", token="garbageToken",
        tests=f"""{status(401)}
pm.test("edge error shape", () => {{
  const b = pm.response.json();
  pm.expect(b.message).to.eql("Invalid or expired token");
  pm.expect(b).to.include.keys("status", "error", "path", "timestamp");
}});"""),
    req("Anonymous call is passed on and refused by the service → 401", "GET", f"{GATEWAY}/api/wallet",
        tests=status(401)),
    req("/internal is not reachable through the gateway → 404", "POST",
        f"{GATEWAY}/internal/products/seller/{{{{sellerId}}}}/product/x/reduce-stock",
        body={"quantity": 1},
        pre="""pm.request.headers.add({ key: "X-Internal-Api-Key", value: "guessed-key" });""",
        tests=status(404)),
    req("Client correlation id is reused and echoed", "GET", f"{GATEWAY}/api/categories",
        pre="""pm.request.headers.add({ key: "X-Correlation-Id", value: "postman-run-0001" });""",
        tests=f"""{status(200)}
pm.test("same id back", () => pm.expect(pm.response.headers.get("X-Correlation-Id")).to.eql("postman-run-0001"));
{ONE_CORRELATION_ID}"""),
    req("Unsafe correlation id is replaced", "GET", f"{GATEWAY}/api/categories",
        pre="""pm.request.headers.add({ key: "X-Correlation-Id", value: "bad id; <script>" });""",
        tests=f"""{status(200)}
pm.test("fresh uuid instead", () => pm.expect(pm.response.headers.get("X-Correlation-Id")).to.match(/^[0-9a-f-]{{36}}$/));"""),
])

# --------------------------------------------------------------------------- Orders – Buyer

def order_body_raw(qty):
    return json.dumps({
        "items": [{"productId": "{{productId}}", "quantity": qty}],
        "shippingAddress": "Str. Testului 1, Sibiu",
    }, indent=2)

TAMPERED_ORDER = json.dumps({
    "buyerId": "{{newUserId}}", "sellerId": "someone", "status": "DELIVERED", "totalAmount": 0.01,
    "items": [{"productId": "{{productId}}", "quantity": 1, "price": 0.01}],
    "shippingAddress": "Str. Testului 1, Sibiu",
}, indent=2)

ADDRESS = {"label": "QA", "recipientName": "Ana Pop", "street": "Str. Florilor 3", "city": "Sibiu",
           "postalCode": "550000", "country": "Romania", "phone": "+40 712 345 678"}

orders_buyer = folder("Orders – Buyer", "Address book, checkout with real-time stock deduction, cancellation with auto-restock, buyer order history.", [
    req("Wallet needs a token → 401 (PLAN 3.7)", "GET", f"{CORE}/api/wallet", tests=status(401)),
    req("Invalid top-ups → 400", "POST", f"{CORE}/api/wallet/top-up", token="buyerToken",
        body={"amount": -5},
        tests=f"""{status(400)}
const bad = [{{ amount: 5000.01 }}, {{ amount: 1.001 }}, {{}}];
bad.forEach(b => pm.sendRequest({{ url: pm.variables.replaceIn("{{{{baseUrlCore}}}}/api/wallet/top-up"), method: "POST",
  header: {{ "Content-Type": "application/json", Authorization: "Bearer " + pm.collectionVariables.get("buyerToken") }},
  body: {{ mode: "raw", raw: JSON.stringify(b) }} }}, (err, res) => pm.test("rejects " + JSON.stringify(b), () => pm.expect(res.code).to.eql(400))));"""),
    req("Buyer tops up the demo wallet (amount only, card data ignored)", "POST", f"{CORE}/api/wallet/top-up", token="buyerToken",
        body={"amount": 1000, "cardNumber": "4111111111111111"},
        tests=f"""{status(200)}
{JSON_OK}
pm.test("balance in cents and RON", () => {{
  pm.expect(body.balanceCents).to.be.a("number").and.at.least(100000);
  pm.expect(body.currency).to.eql("RON");
}});
pm.test("no card data echoed", () => pm.expect(JSON.stringify(body)).to.not.include("4111"));
pm.collectionVariables.set("walletBefore", body.balanceCents);"""),
    req("Address book needs a token → 401", "GET", f"{CORE}/api/user/addresses", tests=status(401)),
    req("Buyer saves a shipping address", "POST", f"{CORE}/api/user/addresses", token="buyerToken",
        body=ADDRESS,
        tests=f"""{status(201)}
{JSON_OK}
pm.test("has an id and the fields", () => {{
  pm.expect(body.id).to.be.a("string").and.not.empty;
  pm.expect(body.city).to.eql("Sibiu");
}});
pm.collectionVariables.set("addressId", body.id);"""),
    req("Invalid address → 400", "POST", f"{CORE}/api/user/addresses", token="buyerToken",
        body={**ADDRESS, "street": "", "phone": "<script>"}, tests=status(400)),
    req("Buyer lists own addresses (exactly one default)", "GET", f"{CORE}/api/user/addresses", token="buyerToken",
        tests=f"""{status(200)}
const list = pm.response.json();
pm.test("contains the new address", () => pm.expect(list.map(a => a.id)).to.include(pm.collectionVariables.get("addressId")));
pm.test("exactly one default", () => pm.expect(list.filter(a => a.default)).to.have.lengthOf(1));"""),
    req("Buyer makes it the default", "POST", f"{CORE}/api/user/addresses/{{{{addressId}}}}/default", token="buyerToken",
        tests=f"""{status(200)}
pm.test("is default", () => pm.expect(pm.response.json().default).to.be.true);"""),
    req("Other user cannot touch the buyer's address → 404", "PUT", f"{CORE}/api/user/addresses/{{{{addressId}}}}", token="newUserToken",
        body=ADDRESS, tests=status(404)),
    req("Checkout with someone else's address → 404, no stock change", "POST", f"{CORE}/api/orders", token="newUserToken",
        body={"addressId": "{{addressId}}", "items": [{"productId": "{{productId}}", "quantity": 1}]},
        tests=f"""{status(404)}
{expect_stock(10)}"""),
    req("Checkout with an empty wallet → 409, no stock change (PLAN 3.7)", "POST", f"{CORE}/api/orders", token="newUserToken",
        body={"shippingAddress": "Str. Lipsei 1, Sibiu", "items": [{"productId": "{{productId}}", "quantity": 1}]},
        tests=f"""{status(409)}
pm.test("explains the balance", () => pm.expect(pm.response.json().message).to.match(/insufficient wallet balance/i));
{expect_stock(10)}"""),
    req("Checkout without any address → 400, no stock change", "POST", f"{CORE}/api/orders", token="newUserToken",
        body={"items": [{"productId": "{{productId}}", "quantity": 1}]},
        tests=f"""{status(400)}
pm.test("asks for an address", () => pm.expect(pm.response.json().message).to.match(/shipping address/i));
{expect_stock(10)}"""),
    req("Create order A (qty 2, saved address) → stock 10→8", "POST", f"{CORE}/api/orders", token="buyerToken",
        body={"addressId": "{{addressId}}", "items": [{"productId": "{{productId}}", "quantity": 2}]},
        tests=f"""{status(201)}
{JSON_OK}
pm.test("ships to the formatted address-book entry", () =>
  pm.expect(body.shippingAddress).to.eql("Ana Pop, Str. Florilor 3, 550000 Sibiu, Romania, tel. +40 712 345 678"));
pm.sendRequest({{ url: pm.variables.replaceIn("{{{{baseUrlCore}}}}/api/wallet"), method: "GET",
  header: {{ Authorization: "Bearer " + pm.collectionVariables.get("buyerToken") }} }}, (err, res) => {{
  pm.test("wallet debited by the order total (PLAN 3.7)", () =>
    pm.expect(res.json().balanceCents).to.eql(Number(pm.collectionVariables.get("walletBefore")) - Math.round(body.totalAmount * 100)));
}});
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
    req("Buyer deletes the address (keeps the book small across runs)", "DELETE", f"{CORE}/api/user/addresses/{{{{addressId}}}}", token="buyerToken",
        tests=f"""{status(204)}
pm.sendRequest({{ url: pm.variables.replaceIn("{{{{baseUrlCore}}}}/api/user/addresses"), method: "GET",
  header: {{ Authorization: "Bearer " + pm.collectionVariables.get("buyerToken") }} }}, (err, res) => {{
  pm.test("address gone", () => pm.expect(res.json().map(a => a.id)).to.not.include(pm.collectionVariables.get("addressId")));
}});"""),
    req("Cancel order A → CANCELLED, stock 8→10", "POST", f"{CORE}/api/orders/{{{{orderAId}}}}/cancel", token="buyerToken",
        body={"reason": "Changed my mind"},
        tests=f"""{status(200)}
{JSON_OK}
pm.test("status CANCELLED with reason", () => {{
  pm.expect(body.status).to.eql("CANCELLED");
  pm.expect(body.cancellationReason).to.eql("Changed my mind");
}});
{expect_stock(10)}"""),
    req("Cancelling refunded the wallet; ledger shows payment + refund (PLAN 3.7)", "GET", f"{CORE}/api/wallet/transactions?limit=10", token="buyerToken",
        tests=f"""{status(200)}
const tx = pm.response.json().filter(t => t.orderId === pm.collectionVariables.get("orderAId"));
pm.test("PAYMENT and REFUND of the same amount", () => {{
  const pay = tx.find(t => t.type === "PAYMENT"), ref = tx.find(t => t.type === "REFUND");
  pm.expect(pay, "payment").to.exist;
  pm.expect(ref, "refund").to.exist;
  pm.expect(ref.amountCents).to.eql(-pay.amountCents);
}});
pm.test("balance back to before the order", () =>
  pm.expect(pm.response.json()[0].balanceAfterCents).to.eql(Number(pm.collectionVariables.get("walletBefore"))));"""),
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
    req("Confirm receipt before shipping → 409 (PLAN 2.4)", "POST", f"{CORE}/api/orders/{{{{orderBId}}}}/confirm-receipt", token="buyerToken",
        tests=f"""{status(409)}
pm.test("explains the transition", () => pm.expect(pm.response.json().message).to.match(/PENDING to DELIVERED/));"""),
    req("Order over available stock → 409", "POST", f"{CORE}/api/orders", token="buyerToken",
        body=order_body_raw(9999),
        tests=f"""{status(409)}
pm.test("explains insufficient stock", () => pm.expect(pm.response.json().message).to.match(/insufficient stock/i));
{expect_stock(9)}"""),
    req("Order without token → 401", "POST", f"{CORE}/api/orders",
        body=order_body_raw(1), tests=status(401)),
    req("Tampered price/total/status/buyer are ignored (PLAN 1.5)", "POST", f"{CORE}/api/orders", token="buyerToken",
        body=TAMPERED_ORDER,
        tests=f"""{status(201)}
{JSON_OK}
pm.test("server price and total", () => {{
  pm.expect(body.items[0].price).to.eql(Number(pm.collectionVariables.get("productPrice")));
  pm.expect(body.totalAmount).to.eql(Number(pm.collectionVariables.get("productPrice")));
}});
pm.test("buyer from token, PENDING, real seller", () => {{
  pm.expect(body.buyerId).to.eql(pm.collectionVariables.get("buyerId"));
  pm.expect(body.status).to.eql("PENDING");
  pm.expect(body.sellerId).to.eql(pm.collectionVariables.get("sellerId"));
}});
pm.collectionVariables.set("orderTamperedId", body.id);
{expect_stock(8)}"""),
    req("Cancel tampered order → stock back to 9", "POST", f"{CORE}/api/orders/{{{{orderTamperedId}}}}/cancel", token="buyerToken",
        body={"reason": "test cleanup"},
        tests=f"""{status(200)}
{expect_stock(9)}"""),
    req("Other user cannot read order B → 403 (PLAN 1.4)", "GET", f"{CORE}/api/orders/{{{{orderBId}}}}", token="newUserToken",
        tests=status(403)),
    req("Other user cannot cancel order B → 403 (PLAN 1.4)", "POST", f"{CORE}/api/orders/{{{{orderBId}}}}/cancel", token="newUserToken",
        body={"reason": "griefing"}, tests=f"""{status(403)}
{expect_stock(9)}"""),
    req("Other user cannot list buyer's orders → 403 (PLAN 1.4)", "GET", f"{CORE}/api/orders/buyer/{{{{buyerId}}}}", token="newUserToken",
        tests=status(403)),
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
    req("Seller edits items: qty 1→2 → stock 9→8 (PLAN 2.5)", "PUT", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/edit", token="sellerToken",
        body={"items": [{"productId": "{{productId}}", "quantity": 2}]},
        tests=f"""{status(200)}
pm.test("total recomputed from server price", () => pm.expect(pm.response.json().order.totalAmount).to.eql(2 * Number(pm.collectionVariables.get("productPrice"))));
{expect_stock(8)}"""),
    req("Seller edits items: qty 2→1 → stock 8→9", "PUT", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/edit", token="sellerToken",
        body={"items": [{"productId": "{{productId}}", "quantity": 1}]},
        tests=f"""{status(200)}
{expect_stock(9)}"""),
    req("Edit to more than stock → 409, nothing changes", "PUT", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/edit", token="sellerToken",
        body={"items": [{"productId": "{{productId}}", "quantity": 999}]},
        tests=f"""{status(409)}
{expect_stock(9)}"""),
    req("Seller moves order B to PROCESSING", "PATCH", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/status", token="sellerToken",
        body={"status": "PROCESSING", "estimatedDeliveryDays": "3"},
        tests=f"""{status(200)}
{JSON_OK}
pm.test("status PROCESSING, ETA 3 days", () => {{
  pm.expect(body.order.status).to.eql("PROCESSING");
  pm.expect(body.order.estimatedDeliveryDays).to.eql(3);
}});"""),
    req("Edit locked once PROCESSING → 409", "PUT", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/edit", token="sellerToken",
        body={"shippingAddress": "Too late"},
        tests=f"""{status(409)}
pm.test("explains order is locked", () => pm.expect(pm.response.json().message).to.match(/locked/i));"""),
    req("Invalid status value → 400", "PATCH", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/status", token="sellerToken",
        body={"status": "TELEPORTED"}, tests=status(400)),
    req("Cannot ship without a label/tracking → 409 (PLAN 3.6)", "PATCH", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/status", token="sellerToken",
        body={"status": "SHIPPED"},
        tests=f"""{status(409)}
pm.test("asks for a label", () => pm.expect(pm.response.json().message).to.match(/shipping label/i));"""),
    req("Ship with Wovely: generate eco shipping label (PLAN 3.6)", "POST", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/eco-label", token="sellerToken",
        tests=f"""{status(200)}
{JSON_OK}
pm.test("label returned", () => pm.expect(body.label).to.be.a("string").and.not.empty);
pm.test("tracking number issued by the carrier", () => {{
  pm.expect(body.order.trackingNumber).to.match(/^WVL-(GRL|ECP|BIO|EFD)-[A-Z2-9]{{10}}$/);
  pm.expect(body.label).to.include(body.order.trackingNumber);
}});
pm.test("carrier and CO2 estimate stored", () => {{
  pm.expect(body.order.carrierName).to.be.oneOf(["GreenLogistics", "EcoParcel", "BioShip", "EarthFriendly Delivery"]);
  pm.expect(body.order.estimatedCo2Kg).to.be.a("number").and.at.least(0);
  pm.expect(body.order.shippingDistanceKm).to.be.within(5, 1200);
}});
pm.collectionVariables.set("orderBTracking", body.order.trackingNumber);"""),
    req("Second label for the same order → 409", "POST", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/eco-label", token="sellerToken",
        tests=status(409)),
    req("Label for a cancelled order → 409", "POST", f"{CORE}/api/seller/orders/{{{{orderAId}}}}/eco-label", token="sellerToken",
        tests=status(409)),
    req("Seller moves order B to SHIPPED", "PATCH", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/status", token="sellerToken",
        body={"status": "SHIPPED"},
        tests=f"""{status(200)}
pm.test("status SHIPPED", () => pm.expect(pm.response.json().order.status).to.eql("SHIPPED"));"""),
    req("Seller cannot move SHIPPED back to PENDING → 409 (PLAN 2.4)", "PATCH", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/status", token="sellerToken",
        body={"status": "PENDING"}, tests=status(409)),
    req("Seller cannot refund → 409 (admin only)", "PATCH", f"{CORE}/api/seller/orders/{{{{orderBId}}}}/status", token="sellerToken",
        body={"status": "REFUNDED"}, tests=status(409)),
    req("Buyer cannot cancel a shipped order → 409", "POST", f"{CORE}/api/orders/{{{{orderBId}}}}/cancel", token="buyerToken",
        body={"reason": "too late"}, tests=f"""{status(409)}
{expect_stock(9)}"""),
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
    req("Seller cannot create manual order for another seller → 403 (PLAN 1.4)", "POST", f"{CORE}/api/seller/orders/manual", token="sellerToken",
        body={"sellerId": "{{buyerId}}", "sellerName": "x", "buyerName": "y", "shippingAddress": "z",
              "items": [{"productId": "{{productId}}", "productName": "QA Woven Basket", "quantity": 1, "price": 1}]},
        tests=status(403)),
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
    req("Search users by email (case-insensitive, PLAN 3.1)", "GET",
        f"{CORE}/api/admin/users?query={{{{upperEmail}}}}", token="adminToken",
        pre="""pm.variables.set("upperEmail", encodeURIComponent(pm.collectionVariables.get("newEmail").toUpperCase()));""",
        tests=f"""{status(200)}
const users = pm.response.json();
pm.test("exactly the new user", () => {{
  pm.expect(users).to.have.lengthOf(1);
  pm.expect(users[0].username).to.eql(pm.collectionVariables.get("newUsername"));
}});
pm.test("no password in CRM view", () => pm.expect(users[0]).to.not.have.property("password"));"""),
    req("Search users by username + status filter", "GET",
        f"{CORE}/api/admin/users?query=seller&status=ACTIVE", token="adminToken",
        tests=f"""{status(200)}
const users = pm.response.json();
pm.test("every hit matches the query and status", () => users.forEach(u => {{
  pm.expect((u.username + " " + u.email + " " + (u.fullName || "")).toLowerCase()).to.include("seller");
  pm.expect(u.accountStatus).to.eql("ACTIVE");
}}));
pm.test("seeded seller found", () => pm.expect(users.map(u => u.username)).to.include("seller"));"""),
    req("Search treats regex metacharacters literally", "GET", f"{CORE}/api/admin/users?query=.*", token="adminToken",
        tests=f"""{status(200)}
pm.test("no regex injection", () => pm.expect(pm.response.json()).to.have.lengthOf(0));"""),
    req("Search with unknown status → 400", "GET", f"{CORE}/api/admin/users?status=EXILED", token="adminToken",
        tests=status(400)),
    req("Seller cannot search users → 403", "GET", f"{CORE}/api/admin/users?query=a", token="sellerToken",
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
    req("New user signs in again (refresh token for the ban test)", "POST", f"{CORE}/api/auth/signin",
        body={"username": "{{newUsername}}", "password": "secret123"},
        tests=f"""{status(200)}
pm.collectionVariables.set("refreshC", pm.response.json().refreshToken);"""),
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
    req("Suspension revoked the refresh token → 401 (PLAN 2.9)", "POST", f"{CORE}/api/auth/refresh",
        body={"refreshToken": "{{refreshC}}"}, tests=status(401)),
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
    req("Mark order B disputed (PLAN 2.3)", "POST", f"{CORE}/api/admin/orders/{{{{orderBId}}}}/intervene", token="adminToken",
        body={"action": "MARK_DISPUTED", "reason": "Buyer says parcel is damaged"},
        tests=f"""{status(200)}
pm.test("DISPUTED", () => pm.expect(pm.response.json().status).to.eql("DISPUTED"));"""),
    req("Dispute flag is persisted", "GET", f"{CORE}/api/admin/orders/{{{{orderBId}}}}", token="adminToken",
        tests=f"""{status(200)}
{JSON_OK}
pm.test("isDisputed + reason survived the save", () => {{
  pm.expect(body.isDisputed).to.eql(true);
  pm.expect(body.disputeReason).to.eql("Buyer says parcel is damaged");
}});"""),
    req("Disputed orders list contains order B", "GET", f"{CORE}/api/admin/orders/disputed", token="adminToken",
        tests=f"""{status(200)}
pm.test("listed", () => pm.expect(pm.response.json().map(o => o.id)).to.include(pm.collectionVariables.get("orderBId")));"""),
    req("Resolve dispute without newStatus → 400", "POST", f"{CORE}/api/admin/orders/{{{{orderBId}}}}/intervene", token="adminToken",
        body={"action": "RESOLVE_DISPUTE"}, tests=status(400)),
    req("Resolve dispute → back to SHIPPED", "POST", f"{CORE}/api/admin/orders/{{{{orderBId}}}}/intervene", token="adminToken",
        body={"action": "RESOLVE_DISPUTE", "newStatus": "SHIPPED", "reason": "Photos show intact parcel"},
        tests=f"""{status(200)}
{JSON_OK}
pm.test("flag cleared, SHIPPED", () => {{
  pm.expect(body.status).to.eql("SHIPPED");
  pm.expect(body.isDisputed).to.eql(false);
}});"""),
    req("Unknown intervention → 400", "POST", f"{CORE}/api/admin/orders/{{{{orderBId}}}}/intervene", token="adminToken",
        body={"action": "TELEPORT"}, tests=status(400)),
    req("Audit trail records the intervention", "GET", f"{CORE}/api/admin/orders/audit", token="adminToken",
        tests=f"""{status(200)}
const logs = pm.response.json();
pm.test("has an ORDER_INTERVENTION entry for order B", () => pm.expect(
  logs.some(l => l.targetId === pm.collectionVariables.get("orderBId") && l.actionType === "ORDER_INTERVENTION")).to.be.true);
pm.test("records the admin as actor (PLAN 2.7)", () => pm.expect(
  logs.find(l => l.actionType === "ORDER_INTERVENTION").adminId).to.eql(pm.collectionVariables.get("adminId")));
pm.test("user penalties are audited (PLAN 2.7)", () => pm.expect(
  logs.some(l => l.actionType === "USER_PENALTY" && l.targetId === pm.collectionVariables.get("newUserId"))).to.be.true);
pm.test("product moderation is audited (PLAN 2.7)", () => pm.expect(
  logs.some(l => l.actionType === "PRODUCT_STATUS" && l.targetId === pm.collectionVariables.get("productId"))).to.be.true);"""),
    req("Seller cannot intervene → 403", "POST", f"{CORE}/api/admin/orders/{{{{orderBId}}}}/intervene", token="sellerToken",
        body={"action": "FORCE_REFUND"}, tests=status(403)),
])

# --------------------------------------------------------------------------- Chat

chat = folder("Chat", "Pre-purchase messaging between buyer and seller.", [
    req("Buyer messages seller about product", "POST", f"{CORE}/api/chat/send", token="buyerToken",
        body={"senderId": "{{sellerId}}", "receiverId": "{{sellerId}}", "productId": "{{productId}}",
              "content": "Hi! Could you make this basket in blue?"},
        tests=f"""{status(201)}
{JSON_OK}
pm.test("sender comes from the token, not the body (PLAN 1.6)", () => pm.expect(body.senderId).to.eql(pm.collectionVariables.get("buyerId")));
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
    req("Other user cannot read buyer↔seller conversation → 403 (PLAN 1.6)", "GET", f"{CORE}/api/chat/conversation?user1={{{{buyerId}}}}&user2={{{{sellerId}}}}", token="newUserToken",
        tests=status(403)),
    req("Other user cannot read seller's unread list → 403 (PLAN 1.6)", "GET", f"{CORE}/api/chat/unread/{{{{sellerId}}}}", token="newUserToken",
        tests=status(403)),
    req("Chat without token → 401", "GET", f"{CORE}/api/chat/unread/{{{{sellerId}}}}", tests=status(401)),
])

# --------------------------------------------------------------------------- Reviews

reviews = folder("Reviews", "Product reviews (products service) and seller reviews (core). "
                 "\"Verified Purchase\" = the author has a DELIVERED/COMPLETED order with the product / seller (PLAN 3.3).", [
    req("Anonymous cannot review → 401 (PLAN 1.7)", "POST", f"{PRODUCTS}/api/reviews",
        body={"productId": "{{productId}}", "rating": 1, "comment": "fake"}, tests=status(401)),
    req("Buyer confirms receipt of order B (makes the purchase verifiable)", "POST", f"{CORE}/api/orders/{{{{orderBId}}}}/confirm-receipt", token="buyerToken",
        tests=f"""{status(200)}
pm.test("DELIVERED", () => pm.expect(pm.response.json().status).to.eql("DELIVERED"));"""),
    req("Buyer reviews product", "POST", f"{PRODUCTS}/api/reviews", token="buyerToken",
        body={"productId": "{{productId}}", "userId": "{{sellerId}}", "username": "Impostor",
              "rating": 5, "comment": "Beautiful craftsmanship.", "photoUrls": []},
        tests=f"""{status(201)}
pm.test("rating stored", () => pm.expect(pm.response.json().rating).to.eql(5));
pm.test("author comes from the token (PLAN 1.7)", () => {{
  pm.expect(pm.response.json().userId).to.eql(pm.collectionVariables.get("buyerId"));
  pm.expect(pm.response.json().username).to.eql("testuser");
}});
pm.test("Verified Purchase — buyer received order B (PLAN 3.3)", () => pm.expect(pm.response.json().verifiedPurchase).to.be.true);"""),
    req("Second review of same product → 409 (PLAN 1.7)", "POST", f"{PRODUCTS}/api/reviews", token="buyerToken",
        body={"productId": "{{productId}}", "rating": 1, "comment": "again"}, tests=status(409)),
    req("Non-buyer review is not verified, even if the client claims so (PLAN 3.3)", "POST", f"{PRODUCTS}/api/reviews", token="newUserToken",
        body={"productId": "{{productId}}", "rating": 4, "comment": "Saw it at a fair.", "verifiedPurchase": True},
        tests=f"""{status(201)}
pm.test("verifiedPurchase forced to false", () => pm.expect(pm.response.json().verifiedPurchase).to.be.false);"""),
    req("Product reviews with average", "GET", f"{PRODUCTS}/api/reviews/product/{{{{productId}}}}",
        tests=f"""{status(200)}
{JSON_OK}
pm.test("has review and average", () => {{
  pm.expect(body.totalReviews).to.be.at.least(1);
  pm.expect(body.averageRating).to.be.within(1, 5);
}});
pm.test("verified and unverified reviews are distinguishable", () => {{
  const flags = body.reviews.map(r => r.verifiedPurchase);
  pm.expect(flags).to.include(true);
  pm.expect(flags).to.include(false);
}});"""),
    req("Buyer marks order B item reviewed", "POST", f"{CORE}/api/orders/{{{{orderBId}}}}/mark-reviewed", token="buyerToken",
        body='["{{productId}}"]',
        tests=f"""{status(200)}
pm.sendRequest({{ url: pm.variables.replaceIn("{{{{baseUrlCore}}}}/api/orders/{{{{orderBId}}}}"), method: "GET",
  header: {{ Authorization: "Bearer " + pm.collectionVariables.get("buyerToken") }} }}, (err, res) => {{
  pm.test("item flagged reviewed", () => pm.expect(res.json().items[0].reviewed).to.be.true);
}});"""),
    req("Anonymous cannot review seller → 401 (PLAN 1.7)", "POST", f"{CORE}/api/public/users/seller/{{{{sellerId}}}}/reviews",
        body={"overallRating": 1, "communicationRating": 1, "shippingRating": 1, "comment": "fake"}, tests=status(401)),
    req("Buyer reviews seller", "POST", f"{CORE}/api/public/users/seller/{{{{sellerId}}}}/reviews", token="buyerToken",
        body={"buyerId": "{{buyerId}}", "buyerName": "testuser", "overallRating": 5,
              "communicationRating": 5, "shippingRating": 4, "comment": "Fast replies."},
        tests=f"""{status(200)}
pm.test("bound to seller", () => pm.expect(pm.response.json().sellerId).to.eql(pm.collectionVariables.get("sellerId")));
pm.test("buyer comes from the token", () => pm.expect(pm.response.json().buyerId).to.eql(pm.collectionVariables.get("buyerId")));
pm.test("Verified Purchase from this seller (PLAN 3.3)", () => pm.expect(pm.response.json().verifiedPurchase).to.be.true);"""),
    req("Non-customer seller review is not verified (PLAN 3.3)", "POST", f"{CORE}/api/public/users/seller/{{{{sellerId}}}}/reviews", token="newUserToken",
        body={"overallRating": 3, "communicationRating": 3, "shippingRating": 3, "comment": "Only chatted.", "verifiedPurchase": True},
        tests=f"""{status(200)}
pm.test("verifiedPurchase forced to false", () => pm.expect(pm.response.json().verifiedPurchase).to.be.false);"""),
    req("Seller reviews list", "GET", f"{CORE}/api/public/users/seller/{{{{sellerId}}}}/reviews",
        tests=f"""{status(200)}
pm.test("at least one review", () => pm.expect(pm.response.json()).to.be.an("array").that.is.not.empty);"""),
])

# --------------------------------------------------------------------------- Returns

returns = folder("Returns", "Return requests on delivered orders.", [
    req("Order B is already DELIVERED (precondition, confirmed in Reviews)", "GET", f"{CORE}/api/orders/{{{{orderBId}}}}", token="buyerToken",
        tests=f"""{status(200)}
pm.test("DELIVERED", () => pm.expect(pm.response.json().status).to.eql("DELIVERED"));"""),
    req("Other user cannot open a return on order B → 403 (PLAN 1.4)", "POST", f"{CORE}/api/returns", token="newUserToken",
        body={"orderId": "{{orderBId}}", "items": [{"productId": "{{productId}}"}]}, tests=status(403)),
    req("Return on a cancelled order → 409 (PLAN 2.6)", "POST", f"{CORE}/api/returns", token="buyerToken",
        body={"orderId": "{{orderAId}}", "items": [{"productId": "{{productId}}"}]}, tests=status(409)),
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
pm.test("status and amounts come from the server", () => {{
  pm.expect(body.status).to.eql("PICKED_UP");
  pm.expect(body.buyerId).to.eql(pm.collectionVariables.get("buyerId"));
  pm.expect(body.totalAmount).to.eql(Number(pm.collectionVariables.get("productPrice")));
}});
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
    req("Seller refunds the return → stock 8→9, order REFUNDED (PLAN 2.6)", "PATCH", f"{CORE}/api/returns/{{{{returnId}}}}/status", token="sellerToken",
        body={"status": "REFUNDED"},
        tests=f"""{status(200)}
pm.test("REFUNDED", () => pm.expect(pm.response.json().status).to.eql("REFUNDED"));
{expect_stock(9)}
pm.sendRequest({{ url: pm.variables.replaceIn("{{{{baseUrlCore}}}}/api/orders/{{{{orderBId}}}}"), method: "GET",
  header: {{ Authorization: "Bearer " + pm.collectionVariables.get("buyerToken") }} }}, (err, res) => {{
  pm.test("order fully refunded", () => {{
    pm.expect(res.json().refundedAmount).to.eql(Number(pm.collectionVariables.get("productPrice")));
    pm.expect(res.json().status).to.eql("REFUNDED");
  }});
}});"""),
    req("Refunded return cannot move again → 409", "PATCH", f"{CORE}/api/returns/{{{{returnId}}}}/status", token="sellerToken",
        body={"status": "DENIED"}, tests=status(409)),
    req("Invalid return status → 400", "PATCH", f"{CORE}/api/returns/{{{{returnId}}}}/status", token="sellerToken",
        body={"status": "TELEPORTED"}, tests=status(400)),
    req("Buyer cannot update return status → 403", "PATCH", f"{CORE}/api/returns/{{{{returnId}}}}/status", token="buyerToken",
        body={"status": "REFUNDED"}, tests=status(403)),
    req("Admin lists all returns", "GET", f"{CORE}/api/returns/all", token="adminToken",
        tests=f"""{status(200)}
pm.test("is an array", () => pm.expect(pm.response.json()).to.be.an("array").that.is.not.empty);"""),
])

# --------------------------------------------------------------------------- Reports

reports = folder("Reports", "Flagged content queue; admin may read chat logs only through a CHAT report (PLAN 3.2).", [
    req("Buyer reports chat with seller (other user's id)", "POST", f"{CORE}/api/reports", token="buyerToken",
        body={"reporterId": "{{sellerId}}", "reporterName": "forged", "targetType": "CHAT",
              "targetId": "{{sellerId}}", "targetName": "forged", "reason": "Testing moderation flow", "status": "RESOLVED"},
        tests=f"""{status(200)}
{JSON_OK}
pm.test("reporter comes from the token", () => pm.expect(body.reporterId).to.eql(pm.collectionVariables.get("buyerId")));
pm.test("server-set status and name", () => {{
  pm.expect(body.status).to.eql("PENDING");
  pm.expect(body.targetName).to.eql("Chat with seller");
}});
pm.test("participants stored sorted (PLAN 3.2)", () => {{
  const ids = [pm.collectionVariables.get("buyerId"), pm.collectionVariables.get("sellerId")].sort();
  pm.expect(body.participantIds).to.eql(ids);
  pm.expect(body.targetId).to.eql(ids.join(":"));
}});
pm.collectionVariables.set("reportId", body.id);"""),
    req("Same open chat report again → 409", "POST", f"{CORE}/api/reports", token="buyerToken",
        body={"targetType": "CHAT", "targetId": "{{buyerId}}:{{sellerId}}", "reason": "again"}, tests=status(409)),
    req("Cannot report someone else's conversation → 403", "POST", f"{CORE}/api/reports", token="newUserToken",
        body={"targetType": "CHAT", "targetId": "{{buyerId}}:{{sellerId}}", "reason": "snooping"}, tests=status(403)),
    req("Cannot report a conversation that never happened → 400", "POST", f"{CORE}/api/reports", token="newUserToken",
        body={"targetType": "CHAT", "targetId": "{{adminId}}", "reason": "made up"}, tests=status(400)),
    req("Chat report with unknown user → 404", "POST", f"{CORE}/api/reports", token="buyerToken",
        body={"targetType": "CHAT", "targetId": "000000000000000000000000", "reason": "ghost"}, tests=status(404)),
    req("Report without reason → 400", "POST", f"{CORE}/api/reports", token="buyerToken",
        body={"targetType": "PRODUCT", "targetId": "{{productId}}"}, tests=status(400)),
    req("Unknown target type → 400", "POST", f"{CORE}/api/reports", token="buyerToken",
        body={"targetType": "USER", "targetId": "{{sellerId}}", "reason": "x"}, tests=status(400)),
    req("Anonymous cannot report → 401", "POST", f"{CORE}/api/reports",
        body={"targetType": "PRODUCT", "targetId": "{{productId}}", "reason": "x"}, tests=status(401)),
    req("New user reports listing (name from catalog)", "POST", f"{CORE}/api/reports", token="newUserToken",
        body={"targetType": "PRODUCT", "targetId": "{{productId}}", "targetName": "forged", "reason": "Looks mass-produced"},
        tests=f"""{status(200)}
pm.test("targetName is the real product name", () => pm.expect(pm.response.json().targetName).to.not.eql("forged"));
pm.collectionVariables.set("productReportId", pm.response.json().id);"""),
    req("Report unknown product → 400", "POST", f"{CORE}/api/reports", token="buyerToken",
        body={"targetType": "PRODUCT", "targetId": "000000000000000000000000", "reason": "x"}, tests=status(400)),
    req("Admin flagged-content queue", "GET", f"{CORE}/api/reports/all", token="adminToken",
        tests=f"""{status(200)}
pm.test("contains both reports", () => pm.expect(pm.response.json().map(r => r.id))
  .to.include.members([pm.collectionVariables.get("reportId"), pm.collectionVariables.get("productReportId")]));"""),
    req("Admin reads reported chat log", "GET", f"{CORE}/api/reports/chat-log/{{{{reportId}}}}", token="adminToken",
        tests=f"""{status(200)}
const msgs = pm.response.json();
pm.test("only messages between the two participants", () => {{
  const ids = [pm.collectionVariables.get("buyerId"), pm.collectionVariables.get("sellerId")];
  pm.expect(msgs).to.not.be.empty;
  msgs.forEach(m => {{ pm.expect(ids).to.include(m.senderId); pm.expect(ids).to.include(m.receiverId); }});
}});
pm.test("oldest first", () => {{
  for (let i = 1; i < msgs.length; i++) pm.expect(msgs[i].timestamp >= msgs[i - 1].timestamp).to.be.true;
}});"""),
    req("Product report has no chat log → 400", "GET", f"{CORE}/api/reports/chat-log/{{{{productReportId}}}}", token="adminToken",
        tests=status(400)),
    req("Unknown report chat log → 404", "GET", f"{CORE}/api/reports/chat-log/000000000000000000000000", token="adminToken",
        tests=status(404)),
    req("Admin resolves report", "PATCH", f"{CORE}/api/reports/{{{{reportId}}}}/status", token="adminToken",
        body={"status": "RESOLVED"},
        tests=f"""{status(200)}
pm.test("RESOLVED", () => pm.expect(pm.response.json().status).to.eql("RESOLVED"));"""),
    req("Deciding a decided report → 409", "PATCH", f"{CORE}/api/reports/{{{{reportId}}}}/status", token="adminToken",
        body={"status": "DISMISSED"}, tests=status(409)),
    req("Invalid report status → 400", "PATCH", f"{CORE}/api/reports/{{{{productReportId}}}}/status", token="adminToken",
        body={"status": "DELETED"}, tests=status(400)),
    req("Admin dismisses product report", "PATCH", f"{CORE}/api/reports/{{{{productReportId}}}}/status", token="adminToken",
        body={"status": "DISMISSED"},
        tests=f"""{status(200)}
pm.test("DISMISSED", () => pm.expect(pm.response.json().status).to.eql("DISMISSED"));"""),
    req("Decisions are audited", "GET", f"{CORE}/api/admin/orders/audit", token="adminToken",
        tests=f"""{status(200)}
const types = pm.response.json().map(a => a.actionType);
pm.test("REPORT_RESOLVED, REPORT_DISMISSED and CHAT_LOG_VIEWED logged", () =>
  pm.expect(types).to.include.members(["REPORT_RESOLVED", "REPORT_DISMISSED", "CHAT_LOG_VIEWED"]));"""),
    req("Seller cannot decide reports → 403", "PATCH", f"{CORE}/api/reports/{{{{reportId}}}}/status", token="sellerToken",
        body={"status": "DISMISSED"}, tests=status(403)),
    req("Buyer cannot read report queue → 403", "GET", f"{CORE}/api/reports/all", token="buyerToken",
        tests=status(403)),
    req("Buyer cannot read chat logs → 403", "GET", f"{CORE}/api/reports/chat-log/{{{{reportId}}}}", token="buyerToken",
        tests=status(403)),
])

# --------------------------------------------------------------------------- Cleanup

cleanup = folder("Cleanup", "Archives the test product so repeated runs don't pile up catalog entries.", [
    req("Seller deletes (archives) test product", "DELETE", f"{PRODUCTS}/api/products/seller/{{{{sellerId}}}}/product/{{{{productId}}}}", token="sellerToken",
        tests=f"""{status(200)}
pm.sendRequest({{ url: pm.variables.replaceIn("{{{{baseUrlProducts}}}}/api/products/{{{{productId}}}}"), method: "GET" }}, (err, res) => {{
  pm.test("soft-deleted: still resolvable for order history, status ARCHIVED (PLAN 2.8)", () => pm.expect(res.json().status).to.eql("ARCHIVED"));
}});"""),
    req("Archived product left the catalog", "GET", f"{PRODUCTS}/api/products",
        tests=f"""{status(200)}
pm.test("not listed", () => pm.expect(pm.response.json().map(p => p.id)).to.not.include(pm.collectionVariables.get("productId")));"""),
    req("Archived product cannot be ordered → 400", "POST", f"{CORE}/api/orders", token="buyerToken",
        body=order_body_raw(1), tests=status(400)),
])

# --------------------------------------------------------------------------- collections

VARIABLES = [
    "refreshA", "refreshB", "refreshC",
    "orderTamperedId",
    "adminToken", "adminId", "sellerToken", "sellerId", "buyerToken", "buyerId",
    "newUsername", "newEmail", "newUserToken", "newUserId", "garbageToken",
    "rootCategoryId", "rootCategoryName", "productId", "productPrice",
    "orderAId", "orderBId", "orderBNumber", "chatMessageId", "returnId", "reportId", "productReportId", "orderBTracking", "addressId", "walletBefore",
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
    [auth, gateway, products, inventory, orders_buyer, orders_seller, admin, chat, reviews, returns, reports, cleanup],
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
    "local": ("http://localhost:8081", "http://localhost:8082", "http://localhost:8083", "http://localhost:8090"),
    "docker": ("http://wovely-backend:8081", "http://products:8082", "http://inventory:8083", "http://gateway:8090"),
}

def env(name, urls):
    return {
        "id": str(uuid.uuid5(uuid.NAMESPACE_URL, "wovely-env-" + name)),
        "name": f"Wovely – {name}",
        "values": [
            {"key": "baseUrlCore", "value": urls[0], "type": "default", "enabled": True},
            {"key": "baseUrlProducts", "value": urls[1], "type": "default", "enabled": True},
            {"key": "baseUrlInventory", "value": urls[2], "type": "default", "enabled": True},
            {"key": "baseUrlGateway", "value": urls[3], "type": "default", "enabled": True},
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
