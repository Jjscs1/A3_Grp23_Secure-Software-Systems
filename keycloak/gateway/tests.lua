package.path = "/etc/evp-gateway/?.lua;" .. package.path
local window, policy = require("window"), require("policy")
local passed = 0
local function test(name, body)
    body(); passed=passed+1; print("PASS: " .. name)
end
local function budget(key, limit) return {{key=key,limit=limit}} end
local function fixture(capacity)
    local t=100
    return window.new(function() return t end, capacity or 10000), function(x) t=x end
end
test("31st source request returns 60 seconds",function()
    local l=fixture(); for i=1,30 do assert(l:acquire(budget("ip",30))==0) end
    assert(l:acquire(budget("ip",30))==60)
end)
test("rejections do not extend expiry; recovery is automatic",function()
    local l,time=fixture(); l:acquire(budget("a",1)); time(159.2)
    assert(l:acquire(budget("a",1))==1); time(160); assert(l:acquire(budget("a",1))==0)
end)
test("window is rolling across minute boundaries",function()
    local l,time=fixture();time(119.9);l:acquire(budget("a",1));time(120)
    assert(l:acquire(budget("a",1))==60)
end)
test("11th account-plus-IP form is limited",function()
    local l=fixture();local r=policy.classify("/realms/evp/login-actions/authenticate","POST")
    for i=1,10 do assert(l:acquire(policy.account_budget(r,"ip1","userhash"))==0) end
    assert(l:acquire(policy.account_budget(r,"ip1","userhash"))==60)
    assert(l:acquire(policy.account_budget(r,"ip2","userhash"))==0)
end)
test("account and endpoint buckets are separated",function()
    local l=fixture(); local a=policy.classify("/realms/evp/login-actions/authenticate","POST")
    local b=policy.classify("/realms/evp-staff/login-actions/authenticate","POST")
    assert(l:acquire(policy.account_budget(a,"ip","one"))==0)
    for i=2,10 do l:acquire(policy.account_budget(a,"ip","one")) end
    assert(l:acquire(policy.account_budget(a,"ip","two"))==0)
    assert(l:acquire(policy.account_budget(b,"ip","one"))==0)
end)
test("aggregate endpoint limit applies across IPs",function()
    local l=fixture();local r={realm="evp",endpoint="auth",ip=30,total=2}
    assert(l:acquire(policy.ip_budgets(r,"1"))==0)
    assert(l:acquire(policy.ip_budgets(r,"2"))==0)
    assert(l:acquire(policy.ip_budgets(r,"3"))==60)
end)
test("full memory rejects new keys without evicting active limits",function()
    local l,time=fixture(1);l:acquire(budget("a",1));assert(l:acquire(budget("b",1))==60)
    assert(l:acquire(budget("a",1))==60);time(160);assert(l:acquire(budget("b",1))==0)
end)
test("rejected transaction does not consume an unrelated budget",function()
    local l=fixture();l:acquire(budget("a",1))
    assert(l:acquire({{key="a",limit=1},{key="b",limit=1}})==60)
    assert(l:acquire(budget("b",1))==0)
end)
test("username normalization and duplicate rejection",function()
    assert(policy.username({username=" VOTER1 "})=="voter1")
    local name,err=policy.username({username={"victim","attacker"}})
    assert(name==nil and err=="invalid")
    assert(policy.username({password="not-a-username"})==nil)
end)
test("both realms and master login routes are protected",function()
    for _,realm in ipairs({"evp","evp-staff","master"}) do
        for _,path in ipairs({"login-actions/authenticate","login-actions/required-action",
            "protocol/openid-connect/auth","protocol/openid-connect/token",
            "protocol/openid-connect/auth/device"}) do
            assert(policy.classify("/realms/"..realm.."/"..path,"POST"))
        end
    end
end)
test("logout, discovery, keys and static resources are exempt",function()
    for _,path in ipairs({"/realms/evp/protocol/openid-connect/logout",
        "/realms/evp/protocol/openid-connect/certs","/realms/evp/.well-known/openid-configuration",
        "/resources/style.css","/admin/master/console/"}) do
        assert(policy.classify(path,"GET")==nil)
    end
    assert(policy.classify("/realms/evp/protocol/openid-connect/token","OPTIONS")==nil)
end)
test("query/session identifiers are not limiter keys",function()
    local a=policy.classify("/realms/evp/login-actions/authenticate","POST")
    local b=policy.classify("/realms/evp/login-actions/reset-credentials","POST")
    assert(policy.ip_budgets(a,"ip")[1].key==policy.ip_budgets(b,"ip")[1].key)
end)
print("Gateway policy tests passed: "..passed)
