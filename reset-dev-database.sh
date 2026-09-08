#!/bin/bash
# Reset Development Database Script (Linux/Mac)
# This script deletes the H2 database files and restarts with fresh schema

echo "====================================="
echo " Reset Development Database"
echo "====================================="
echo ""

if [ -d "data" ]; then
    echo "[1/3] Stopping any running application..."
    echo "      Please ensure the application is stopped before continuing."
    echo ""
    read -p "Press Enter to continue..."

    echo "[2/3] Deleting database files from 'data/' directory..."
    rm -rf data/
    echo "      Database files deleted successfully."
    echo ""

    echo "[3/3] Database reset complete!"
    echo ""
    echo "Next steps:"
    echo "  1. Run: mvn spring-boot:run -Dspring-boot.run.profiles=dev"
    echo "  2. Sample data from data.sql will load automatically"
    echo "  3. Access H2 Console: http://localhost:8085/h2-console"
    echo "     JDBC URL: jdbc:h2:file:./data/d4c_portal_dev"
    echo ""
else
    echo "[INFO] No 'data/' directory found."
    echo "       Database is already clean or hasn't been created yet."
    echo ""
    echo "Next steps:"
    echo "  1. Run: mvn spring-boot:run -Dspring-boot.run.profiles=dev"
    echo "  2. Database will be created fresh with sample data"
    echo ""
fi
