from collections import Counter
c = Counter(input().strip())
for ch in sorted(c):
    print(ch, c[ch])
