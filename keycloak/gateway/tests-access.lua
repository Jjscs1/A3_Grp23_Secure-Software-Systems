package.path = "/etc/evp-gateway/?.lua;" .. package.path
local original_ngx = ngx
require("resty.sha256")
require("resty.string")
local passed = 0
local function context(path, method, username)
    local data = {time=100, path=path, method=method or "POST", username=username, ip="192.0.2.1"}
    package.loaded.state = nil
    ngx = {
        var={uri=path,remote_addr=data.ip,content_type="application/x-www-form-urlencoded",content_length="20"},
        header={},
        req={
            get_method=function() return data.method end,
            read_body=function() end,
            get_body_file=function() return data.file end,
            get_body_data=function() return data.body or "" end,
            get_post_args=function() return {username=data.username, password="NEVER_STORE_THIS_PASSWORD"}, data.argsError end
        },
        now=function() return data.time end,
        say=function(body) data.responseBody=body end,
        exit=function(status) data.status=status;return status end
    }
    function data:call()
        self.status=nil;self.responseBody=nil;ngx.header={}
        ngx.var.remote_addr=self.ip
        dofile((GATEWAY_DIR or "/etc/evp-gateway/") .. "access.lua")
        return self.status
    end
    return data
end
local function test(name,fn) fn();passed=passed+1;print("PASS: "..name) end
test("anonymous auth GET reaches 429 with headers",function()
    local c=context("/realms/evp/protocol/openid-connect/auth","GET")
    for i=1,30 do assert(c:call()==nil) end
    assert(c:call()==429);assert(ngx.header["Retry-After"]=="60")
    assert(ngx.header["Cache-Control"]=="no-store")
    assert(c.responseBody=='{"error":"RATE_LIMITED"}')
end)
test("same claimed account gets 429 without affecting another source",function()
    local c=context("/realms/evp/login-actions/authenticate","POST","delegate1")
    for i=1,10 do assert(c:call()==nil) end
    assert(c:call()==429)
    c.ip="192.0.2.2";assert(c:call()==nil)
end)
test("account form does not retain raw names or passwords",function()
    local c=context("/realms/evp-staff/login-actions/authenticate","POST","delegate1")
    assert(c:call()==nil)
    for key in pairs(require("state").buckets) do
        assert(not key:find("delegate1",1,true))
        assert(not key:find("NEVER_STORE_THIS_PASSWORD",1,true))
    end
end)
test("token requests without username are still IP limited",function()
    local c=context("/realms/evp/protocol/openid-connect/token","POST")
    for i=1,30 do assert(c:call()==nil) end
    assert(c:call()==429)
    c.time=160;assert(c:call()==nil)
end)
test("large and truncated forms fail closed",function()
    local c=context("/realms/evp/login-actions/authenticate")
    ngx.var.content_length="32769";assert(c:call()==413)
    ngx.var.content_length="20";c.argsError="truncated";assert(c:call()==400)
end)
test("duplicate usernames fail closed",function()
    local c=context("/realms/evp/login-actions/authenticate","POST",{"one","two"})
    assert(c:call()==400)
end)
test("matrix path parameters are rejected",function()
    local c=context("/realms/evp;changing/login-actions/authenticate")
    assert(c:call()==400)
end)
test("DPoP token 429 has CORS headers only for the application",function()
    local c=context("/realms/evp/protocol/openid-connect/token")
    ngx.var.http_origin="http://localhost:5173"
    for i=1,30 do c:call() end
    assert(c:call()==429)
    assert(ngx.header["Access-Control-Allow-Origin"]=="http://localhost:5173")
    ngx.var.http_origin="https://other.example";c:call()
    assert(ngx.header["Access-Control-Allow-Origin"]==nil)
end)
ngx = original_ngx
print("Gateway access-adapter tests passed: "..passed)
