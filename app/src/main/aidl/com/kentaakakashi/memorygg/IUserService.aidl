package com.kentaakakashi.memorygg;

interface IUserService {
    void destroy() = 16777114;
    String getDiagnostics(int appPid) = 1;
    String probeRemoteValue(int appPid, long address) = 2;
}
