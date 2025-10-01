# Shopify ↔️ ReAI Sync

This application bridges Shopify stores with the ReAI accounting platform. It lets a ReAI tenant install the Shopify app, synchronise orders, and view the sync status from a web UI. Use this document together with the code in the reai examples and the Kotlin services to build or adapt your own integration.

# What You Need From ReAI

1. **Log into the [ReAI application](https://app.reai.no/).**
2. **Create an account** (any company works for testing).
3. **Create an OAuth application** inside ReAI (*Settings → Create Apps*) and select the necessary scopes:
    - `customer:read`, `customer:write`
    - `inventory:read`, `inventory:write`
    - **App URL:** `http://localhost:8083`
    - **Redirect URI:**
        - Production: `https://shopify.reai.no/oauth/callback`
        - Local development: `http://localhost:8083/oauth/callback`
4. **Publish the app.**
5. **Install the app** from *Apps → App Store*.
6. **Check the public API documentation** available via Swagger:
    - [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
    - Use it to confirm available endpoints and required scopes.

### Authenticating Against ReAI
1. Launch the Shopify app from inside ReAI so the token is passed to this application. The token is stored by `ReaiConnectionService` and reused until it expires.
2. When tokens expire, revisit the integration entry inside ReAI to refresh it.

## Shopify Setup
- Use a Shopify development store or a production store where you have access.
- Create a custom app and set the base URL to your deployed instance.
- Authorised redirect callback must match `/oauth/callback`.
- Install the app from the Shopify-ReAI UI. After installation you can manage store links and perform order lookups from `src/main/resources/templates/index.html`.

## Data Flow Overview
1. A ReAI tenant opens this app from ReAI and passes an access token (`OrderSearchController` handles the entry flow).
2. The merchant installs the Shopify store through `/oauth/install`; `ShopifyOAuthController` completes the OAuth handshake and persists the store details.
3. When an order is searched, `ShopifyOrderService` fetches order data via the Shopify Admin API.
4. `ReaiOrderSyncService` prepares customer, order, and invoice payloads and uses `ReaiApiClient` to call the ReAI API.
5. Sync results are stored via `ReaiOrderSyncRecordService` so repeat searches show the status immediately.
