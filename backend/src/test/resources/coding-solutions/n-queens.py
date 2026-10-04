n = int(input())
count = 0
def place(r, cols, d1, d2):
    global count
    if r == n:
        count += 1
        return
    for c in range(n):
        if c not in cols and r - c not in d1 and r + c not in d2:
            cols.add(c); d1.add(r - c); d2.add(r + c)
            place(r + 1, cols, d1, d2)
            cols.remove(c); d1.remove(r - c); d2.remove(r + c)
place(0, set(), set(), set())
print(count)
