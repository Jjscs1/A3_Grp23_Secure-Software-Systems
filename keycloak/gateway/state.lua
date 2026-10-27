-- require() caches this object for this worker's lifetime.
return require("window").new(ngx.now, 10000)
