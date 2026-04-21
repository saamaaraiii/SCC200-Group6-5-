#!/bin/bash
# Setup and Run DB_Scc Transport Routing API

set -e

echo "=========================================="
echo "DB_Scc Transport Routing System - Setup"
echo "=========================================="
echo ""

# Check Python
echo "Checking Python installation..."
python3 --version || {
    echo "Error: Python 3 not found. Please install Python 3.8+"
    exit 1
}
echo "✓ Python 3 found"
echo ""

# Install dependencies
echo "Installing Python dependencies..."
pip3 install -r requirements.txt 2>&1 | tail -5
echo "✓ Dependencies installed"
echo ""

# Create cache directory
echo "Setting up cache directory..."
mkdir -p .cache
echo "✓ Cache directory created"
echo ""

# Validate API server
echo "Validating API server..."
python3 -m py_compile api_server.py
echo "✓ API server syntax valid"
echo ""

# Information
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "To start the server, run:"
echo "  python3 api_server.py"
echo ""
echo "To test the server, run:"
echo "  python3 test_api.py"
echo ""
echo "Server will be available at:"
echo "  http://localhost:5000"
echo ""
echo "Documentation:"
echo "  - API_GUIDE.md (comprehensive endpoint reference)"
echo "  - ARCHITECTURE.md (system design & implementation)"
echo "  - USAGE_EXAMPLES.md (code examples)"
echo "  - QUICK_REFERENCE.md (quick lookup)"
echo ""
