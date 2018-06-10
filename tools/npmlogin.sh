#!/usr/bin/expect -f

# set our args into variables
set i 0; foreach n $argv {set "p[incr i]" $n}

set timeout 60
#npm login command, add whatever command-line args are necessary
spawn npm adduser --registry $p1
match_max 100000

expect "Username"
send "$p2\r"

expect "Password"
send "$p3\r"

expect "Email"
send "$p4\r"

expect {
timeout exit 1
eof
}