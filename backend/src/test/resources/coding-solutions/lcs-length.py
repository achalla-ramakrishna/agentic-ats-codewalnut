a = input().strip(); b = input().strip()
prev = [0] * (len(b) + 1)
for ch in a:
    cur = [0]
    for j, cb in enumerate(b):
        cur.append(prev[j] + 1 if ch == cb else max(prev[j + 1], cur[j]))
    prev = cur
print(prev[-1])
