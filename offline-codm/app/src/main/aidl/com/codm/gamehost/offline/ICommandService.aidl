package com.codm.gamehost.offline;
interface ICommandService {
    String execute(String command) = 1;
    void destroy() = 16777114;
}
