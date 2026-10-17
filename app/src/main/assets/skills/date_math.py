"""Days between two dates, or the date N days away. params: start (YYYY-MM-DD, default today), end (YYYY-MM-DD) or days (number, may be negative)"""

import datetime

today = datetime.date.today()
start = datetime.date.fromisoformat(params["start"]) if params.get("start") else today

if params.get("end"):
    end = datetime.date.fromisoformat(params["end"])
    delta = (end - start).days
    weeks, rest = divmod(abs(delta), 7)
    print("%d days (%d weeks %d days) from %s to %s" % (delta, weeks, rest, start, end))
    print("Working days: %d" % sum(1 for n in range(abs(delta)) if (min(start, end) + datetime.timedelta(n)).weekday() < 5))
elif "days" in params:
    target = start + datetime.timedelta(days=int(params["days"]))
    print("%s is %s" % (target, target.strftime("%A")))
else:
    print("Give end or days")
