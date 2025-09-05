import sys
import io
import traceback

def execute(code: str):
    """
    Executes Python code from a string and captures all print output.
    Works in Chaquopy, runs all top-level code, and captures full tracebacks on errors.
    """
    old_stdout = sys.stdout
    sys.stdout = buffer = io.StringIO()

    try:
        # Wrap code in a function so it always runs
        wrapped_code = "def __auto_main():\n"
        for line in code.splitlines():
            wrapped_code += "    " + line + "\n"
        wrapped_code += "__auto_main()\n"

        exec_globals = {}
        exec(wrapped_code, exec_globals)

        output = buffer.getvalue()
        return output if output else "(no output)"

    except Exception:
        # Capture full traceback
        return traceback.format_exc()
    finally:
        sys.stdout = old_stdout