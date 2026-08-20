import sys

def check_braces(filename):
    with open(filename, 'r') as f:
        lines = f.readlines()
        
    stack = []
    for i, line in enumerate(lines):
        for char in line:
            if char == '{':
                stack.append(i + 1)
            elif char == '}':
                if stack:
                    stack.pop()
        
        # MainScreen starts at 1182, stack goes up. Let's trace stack sizes.
        if i + 1 >= 1180 and i + 1 <= 1735:
            # print only if stack size goes below 2 after MainScreen opens
            if i + 1 > 1230 and len(stack) < 2:
                print(f"Line {i+1}: Stack size dropped to {len(stack)}: {line.strip()}")
                
check_braces('app/src/main/java/com/example/MainActivity.kt')
