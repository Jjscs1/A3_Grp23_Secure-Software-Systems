-- No I/O or yielding in acquire: atomic inside one OpenResty worker.
local M = {}
function M.new(time_source, capacity)
    local state = { buckets = {}, size = 0, swept = time_source(), last = time_source() }
    function state:acquire(budgets)
        local now = math.max(time_source(), self.last)
        self.last = now
        if now - self.swept >= 1 or self.size >= capacity then
            for key, q in pairs(self.buckets) do
                if #q == 0 or q[#q] <= now - 60 then
                    self.buckets[key] = nil
                    self.size = self.size - 1
                end
            end
            self.swept = now
        end
        local retry, missing = 0, 0
        for _, budget in ipairs(budgets) do
            local q = self.buckets[budget.key]
            if q then
                local active = {}
                for _, stamp in ipairs(q) do
                    if stamp > now - 60 then active[#active + 1] = stamp end
                end
                self.buckets[budget.key] = active
                if #active >= budget.limit then
                    retry = math.max(retry, math.ceil(active[1] + 60 - now))
                end
            else
                missing = missing + 1
            end
        end
        if retry > 0 then return retry end
        if self.size + missing > capacity then return 60 end
        for _, budget in ipairs(budgets) do
            local q = self.buckets[budget.key]
            if not q then
                q = {}; self.buckets[budget.key] = q; self.size = self.size + 1
            end
            q[#q + 1] = now
        end
        return 0
    end
    return state
end
return M
