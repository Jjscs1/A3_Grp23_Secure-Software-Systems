local M = {}
-- Input path is NGINX's decoded/normalized $uri, never the raw request target.
function M.classify(path, method)
    if method == "OPTIONS" then return nil end
    local realm, action = path:match("^/realms/([^/]+)/protocol/openid%-connect/([^/]+)/?$")
    if realm and (action == "auth" or action == "token" or action == "registrations"
            or action == "auth/device" or action == "ext") then
        return { realm = realm, endpoint = "oidc-" .. action, ip = 30, account = 10, total = 1000 }
    end
    -- Device and extension subpaths also share a bounded endpoint budget.
    realm = path:match("^/realms/([^/]+)/protocol/openid%-connect/auth/device/?$")
        or path:match("^/realms/([^/]+)/protocol/openid%-connect/ext/")
    if realm then return {realm=realm, endpoint="oidc-extra", ip=30, account=10, total=1000} end
    realm = path:match("^/realms/([^/]+)/login%-actions/")
    if realm then return {realm=realm, endpoint="login-actions", ip=30, account=10, total=1000} end
    return nil
end
function M.username(args)
    local value = args.username
    if value == nil then return nil end
    -- Reject duplicates rather than choose a different username from Keycloak.
    if type(value) ~= "string" or #value > 512 then return nil, "invalid" end
    value = value:match("^%s*(.-)%s*$"):lower()
    if value == "" then return nil end
    return value
end
function M.ip_budgets(rule, ip)
    local route = rule.realm .. ":" .. rule.endpoint
    return {
        {key="ip:" .. route .. ":" .. ip, limit=rule.ip},
        {key="endpoint:" .. route, limit=rule.total}
    }
end
function M.account_budget(rule, ip, fingerprint)
    -- Scope an unverified account name to its source IP. Another IP cannot exhaust
    -- the victim's account allowance. This is NOT a global per-account budget.
    return {{key="account-ip:" .. rule.realm .. ":" .. rule.endpoint .. ":" .. ip .. ":" .. fingerprint,
        limit=rule.account}}
end
return M
