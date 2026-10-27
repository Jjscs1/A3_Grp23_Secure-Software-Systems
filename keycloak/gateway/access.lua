-- Matrix parameters can be normalized differently by an upstream Java router.
-- Keycloak's demo routes do not need them; fail closed rather than create new buckets.
if ngx.var.uri:find(";", 1, true) or ngx.var.uri:find("\\", 1, true) then
    return ngx.exit(400)
end
local policy = require("policy")
local rule = policy.classify(ngx.var.uri, ngx.req.get_method())
if not rule then return end
local limiter = require("state")
local function respond(status, retry)
    ngx.status = status
    ngx.header["Content-Type"] = "application/json"
    ngx.header["Cache-Control"] = "no-store"
    if retry then ngx.header["Retry-After"] = tostring(retry) end
    if ngx.var.http_origin == "http://localhost:5173" then
        ngx.header["Access-Control-Allow-Origin"] = "http://localhost:5173"
        ngx.header["Access-Control-Expose-Headers"] = "Retry-After"
        ngx.header["Vary"] = "Origin"
    end
    ngx.say(status == 429 and '{"error":"RATE_LIMITED"}' or '{"error":"INVALID_REQUEST"}')
    return ngx.exit(status)
end
-- Do not trust Forwarded/X-Forwarded-For to select a limiter bucket.
local ip = ngx.var.remote_addr
local retry = limiter:acquire(policy.ip_budgets(rule, ip))
if retry > 0 then return respond(429, retry) end
if ngx.req.get_method() ~= "POST" then return end
local content_type = (ngx.var.content_type or ""):lower()
if content_type:match("^application/x%-www%-form%-urlencoded%s*;?") then
    -- Authentication form bodies stay in memory up to the explicit limit.
    local declared = tonumber(ngx.var.content_length)
    if declared and declared > 32768 then return respond(413) end
    ngx.req.read_body()
    if ngx.req.get_body_file() then return respond(413) end
    local body = ngx.req.get_body_data() or ""
    if #body > 32768 then return respond(413) end
    local args, err = ngx.req.get_post_args(100)
    if err then return respond(400) end
    local username, invalid = policy.username(args)
    args = nil; body = nil
    if invalid then return respond(400) end
    if username then
        local sha256 = require("resty.sha256"):new()
        sha256:update(rule.realm .. "\n" .. username)
        local fingerprint = require("resty.string").to_hex(sha256:final())
        username = nil
        retry = limiter:acquire(policy.account_budget(rule, ip, fingerprint))
        if retry > 0 then return respond(429, retry) end
    end
end
