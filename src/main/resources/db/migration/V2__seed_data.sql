INSERT INTO categories (name, description) VALUES
    ('Electronics', 'Consumer electronics, gadgets, and accessories'),
    ('Home & Kitchen', 'Appliances and tools for the home'),
    ('Books', 'Physical and digital books across genres'),
    ('Sportswear', 'Apparel and gear for sports and fitness'),
    ('Toys & Games', 'Toys, board games, and puzzles');

INSERT INTO products (sku, name, description, price, stock_quantity, active, category_id) VALUES
    ('ELEC-0001', 'Wireless Noise-Cancelling Headphones', 'Over-ear Bluetooth headphones with active noise cancellation', 149.99, 120, TRUE, 1),
    ('ELEC-0002', '4K Streaming Media Player', 'Compact set-top box for streaming in 4K HDR', 49.99, 300, TRUE, 1),
    ('ELEC-0003', 'Portable Power Bank 20000mAh', 'USB-C fast charging power bank', 29.99, 450, TRUE, 1),
    ('ELEC-0004', 'Mechanical Keyboard', 'Hot-swappable mechanical keyboard with RGB backlight', 89.99, 80, TRUE, 1),
    ('ELEC-0005', '27-inch 4K Monitor', 'IPS panel monitor with USB-C input', 329.99, 40, TRUE, 1),
    ('HOME-0001', 'Stainless Steel Air Fryer', '5.5L digital air fryer with 8 presets', 79.99, 150, TRUE, 2),
    ('HOME-0002', 'Robot Vacuum Cleaner', 'App-controlled robot vacuum with mapping', 249.99, 60, TRUE, 2),
    ('HOME-0003', 'Non-Stick Cookware Set', '10-piece non-stick pots and pans set', 119.99, 90, TRUE, 2),
    ('HOME-0004', 'Electric Kettle', '1.7L electric kettle with auto shut-off', 24.99, 200, TRUE, 2),
    ('BOOK-0001', 'The Pragmatic Programmer', 'Classic software craftsmanship book', 39.99, 500, TRUE, 3),
    ('BOOK-0002', 'Designing Data-Intensive Applications', 'Deep dive into distributed data systems', 44.99, 350, TRUE, 3),
    ('BOOK-0003', 'Clean Architecture', 'Software architecture principles and patterns', 34.99, 280, TRUE, 3),
    ('SPORT-0001', 'Running Shoes', 'Lightweight breathable running shoes', 69.99, 220, TRUE, 4),
    ('SPORT-0002', 'Yoga Mat', 'Non-slip 6mm thick yoga mat', 19.99, 400, TRUE, 4),
    ('SPORT-0003', 'Adjustable Dumbbell Set', 'Pair of adjustable dumbbells, 5-25 lbs each', 129.99, 70, TRUE, 4),
    ('TOY-0001', '1000-Piece Jigsaw Puzzle', 'Landscape-themed jigsaw puzzle', 14.99, 260, TRUE, 5),
    ('TOY-0002', 'Strategy Board Game', 'Award-winning strategy board game for 2-4 players', 39.99, 130, TRUE, 5),
    ('TOY-0003', 'Building Blocks Set', '500-piece creative building blocks set', 34.99, 190, TRUE, 5),
    ('ELEC-0006', 'Discontinued Bluetooth Speaker', 'Older model, no longer manufactured', 0.01, 0, FALSE, 1);
