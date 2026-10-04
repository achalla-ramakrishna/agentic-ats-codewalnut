from collections import Counter
s = input().strip()
c = Counter(s)
print(next((i for i, ch in enumerate(s) if c[ch] == 1), -1))
