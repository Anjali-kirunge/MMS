-- =============================================================================
--  Military Asset Management System
--  MySQL 8 schema + seed data
--
--  Database : military_asset_management
--  Usage    : mysql -u root -p < database/military_asset_management.sql
--
--  This script is re-runnable: it drops and recreates the whole schema.
-- =============================================================================

DROP DATABASE IF EXISTS military_asset_management;
CREATE DATABASE military_asset_management
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
USE military_asset_management;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------------------
-- roles
-- -----------------------------------------------------------------------------
CREATE TABLE roles (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(50)  NOT NULL,
  description VARCHAR(255) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_roles_name (name)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- bases
-- -----------------------------------------------------------------------------
CREATE TABLE bases (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  code       VARCHAR(20)  NOT NULL,
  name       VARCHAR(120) NOT NULL,
  location   VARCHAR(200) NULL,
  commander  VARCHAR(120) NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_bases_code (code)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- equipment_types
-- -----------------------------------------------------------------------------
CREATE TABLE equipment_types (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  code        VARCHAR(30)  NOT NULL,
  name        VARCHAR(120) NOT NULL,
  category    VARCHAR(80)  NOT NULL,
  unit        VARCHAR(20)  NOT NULL DEFAULT 'UNIT',
  description VARCHAR(255) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_equipment_types_code (code),
  KEY ix_equipment_types_category (category)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- users  (application accounts)
-- -----------------------------------------------------------------------------
CREATE TABLE users (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  username   VARCHAR(60)  NOT NULL,
  password   VARCHAR(255) NOT NULL,
  full_name  VARCHAR(120) NOT NULL,
  email      VARCHAR(150) NULL,
  role_id    BIGINT       NOT NULL,
  base_id    BIGINT       NULL,
  enabled    TINYINT(1)   NOT NULL DEFAULT 1,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username),
  UNIQUE KEY uk_users_email (email),
  KEY ix_users_role (role_id),
  KEY ix_users_base (base_id),
  CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id),
  CONSTRAINT fk_users_base FOREIGN KEY (base_id) REFERENCES bases (id)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- personnel
-- -----------------------------------------------------------------------------
CREATE TABLE personnel (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  service_number VARCHAR(40)  NOT NULL,
  full_name      VARCHAR(120) NOT NULL,
  rank_title     VARCHAR(60)  NULL,
  base_id        BIGINT       NOT NULL,
  contact        VARCHAR(40)  NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_personnel_service_number (service_number),
  KEY ix_personnel_base (base_id),
  CONSTRAINT fk_personnel_base FOREIGN KEY (base_id) REFERENCES bases (id)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- stock_balances  (current on-hand quantity per base + equipment type)
--   opening_balance : baseline quantity at the start of the ledger
--   on_hand_quantity: live quantity, always >= 0
-- -----------------------------------------------------------------------------
CREATE TABLE stock_balances (
  id                BIGINT   NOT NULL AUTO_INCREMENT,
  base_id           BIGINT   NOT NULL,
  equipment_type_id BIGINT   NOT NULL,
  opening_balance   INT      NOT NULL DEFAULT 0,
  on_hand_quantity  INT      NOT NULL DEFAULT 0,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_stock_base_equipment (base_id, equipment_type_id),
  KEY ix_stock_equipment (equipment_type_id),
  CONSTRAINT chk_stock_opening  CHECK (opening_balance  >= 0),
  CONSTRAINT chk_stock_on_hand  CHECK (on_hand_quantity >= 0),
  CONSTRAINT fk_stock_base FOREIGN KEY (base_id) REFERENCES bases (id),
  CONSTRAINT fk_stock_equipment FOREIGN KEY (equipment_type_id) REFERENCES equipment_types (id)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- purchases
-- -----------------------------------------------------------------------------
CREATE TABLE purchases (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  reference_no VARCHAR(40)   NOT NULL,
  base_id      BIGINT        NOT NULL,
  supplier     VARCHAR(150)  NOT NULL,
  invoice_no   VARCHAR(80)   NULL,
  purchase_date DATE         NOT NULL,
  remarks      VARCHAR(255)  NULL,
  total_cost   DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  created_by   BIGINT        NULL,
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_purchases_reference (reference_no),
  KEY ix_purchases_base_date (base_id, purchase_date),
  CONSTRAINT fk_purchases_base FOREIGN KEY (base_id) REFERENCES bases (id),
  CONSTRAINT fk_purchases_user FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE purchase_items (
  id                BIGINT        NOT NULL AUTO_INCREMENT,
  purchase_id       BIGINT        NOT NULL,
  equipment_type_id BIGINT        NOT NULL,
  quantity          INT           NOT NULL,
  unit_cost         DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  PRIMARY KEY (id),
  UNIQUE KEY uk_purchase_items_line (purchase_id, equipment_type_id),
  KEY ix_purchase_items_equipment (equipment_type_id),
  CONSTRAINT chk_purchase_items_qty CHECK (quantity > 0),
  CONSTRAINT fk_purchase_items_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id) ON DELETE CASCADE,
  CONSTRAINT fk_purchase_items_equipment FOREIGN KEY (equipment_type_id) REFERENCES equipment_types (id)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- transfers
-- -----------------------------------------------------------------------------
CREATE TABLE transfers (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  reference_no        VARCHAR(40)  NOT NULL,
  source_base_id      BIGINT       NOT NULL,
  destination_base_id BIGINT       NOT NULL,
  transfer_date       DATE         NOT NULL,
  remarks             VARCHAR(255) NULL,
  created_by          BIGINT       NULL,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_transfers_reference (reference_no),
  KEY ix_transfers_source (source_base_id, transfer_date),
  KEY ix_transfers_destination (destination_base_id, transfer_date),
  CONSTRAINT chk_transfers_distinct CHECK (source_base_id <> destination_base_id),
  CONSTRAINT fk_transfers_source FOREIGN KEY (source_base_id) REFERENCES bases (id),
  CONSTRAINT fk_transfers_destination FOREIGN KEY (destination_base_id) REFERENCES bases (id),
  CONSTRAINT fk_transfers_user FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE transfer_items (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  transfer_id       BIGINT NOT NULL,
  equipment_type_id BIGINT NOT NULL,
  quantity          INT    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_transfer_items_line (transfer_id, equipment_type_id),
  KEY ix_transfer_items_equipment (equipment_type_id),
  CONSTRAINT chk_transfer_items_qty CHECK (quantity > 0),
  CONSTRAINT fk_transfer_items_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id) ON DELETE CASCADE,
  CONSTRAINT fk_transfer_items_equipment FOREIGN KEY (equipment_type_id) REFERENCES equipment_types (id)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- assignments
-- -----------------------------------------------------------------------------
CREATE TABLE assignments (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  base_id           BIGINT       NOT NULL,
  equipment_type_id BIGINT       NOT NULL,
  personnel_id      BIGINT       NOT NULL,
  quantity          INT          NOT NULL,
  assigned_date     DATE         NOT NULL,
  remarks           VARCHAR(255) NULL,
  created_by        BIGINT       NULL,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_assignments_base_date (base_id, assigned_date),
  KEY ix_assignments_personnel (personnel_id),
  KEY ix_assignments_equipment (equipment_type_id),
  CONSTRAINT chk_assignments_qty CHECK (quantity > 0),
  CONSTRAINT fk_assignments_base FOREIGN KEY (base_id) REFERENCES bases (id),
  CONSTRAINT fk_assignments_equipment FOREIGN KEY (equipment_type_id) REFERENCES equipment_types (id),
  CONSTRAINT fk_assignments_personnel FOREIGN KEY (personnel_id) REFERENCES personnel (id),
  CONSTRAINT fk_assignments_user FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- expenditures
-- -----------------------------------------------------------------------------
CREATE TABLE expenditures (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  base_id           BIGINT       NOT NULL,
  equipment_type_id BIGINT       NOT NULL,
  quantity          INT          NOT NULL,
  expended_date     DATE         NOT NULL,
  reason            VARCHAR(150) NOT NULL,
  remarks           VARCHAR(255) NULL,
  created_by        BIGINT       NULL,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_expenditures_base_date (base_id, expended_date),
  KEY ix_expenditures_equipment (equipment_type_id),
  CONSTRAINT chk_expenditures_qty CHECK (quantity > 0),
  CONSTRAINT fk_expenditures_base FOREIGN KEY (base_id) REFERENCES bases (id),
  CONSTRAINT fk_expenditures_equipment FOREIGN KEY (equipment_type_id) REFERENCES equipment_types (id),
  CONSTRAINT fk_expenditures_user FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- audit_logs  (append-only: application exposes no update/delete API)
-- -----------------------------------------------------------------------------
CREATE TABLE audit_logs (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       NULL,
  username    VARCHAR(60)  NULL,
  action      VARCHAR(40)  NOT NULL,
  entity      VARCHAR(60)  NOT NULL,
  entity_id   BIGINT       NULL,
  base_id     BIGINT       NULL,
  description VARCHAR(500) NOT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_audit_created (created_at),
  KEY ix_audit_entity (entity, entity_id),
  KEY ix_audit_user (user_id),
  KEY ix_audit_base (base_id),
  CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
  CONSTRAINT fk_audit_base FOREIGN KEY (base_id) REFERENCES bases (id) ON DELETE SET NULL
) ENGINE = InnoDB;

-- =============================================================================
--  SEED DATA
-- =============================================================================

-- Roles -----------------------------------------------------------------------
INSERT INTO roles (id, name, description) VALUES
  (1, 'ADMIN',             'Full system access'),
  (2, 'BASE_COMMANDER',    'Full access restricted to the assigned base'),
  (3, 'LOGISTICS_OFFICER', 'Manages purchases and inter-base transfers');

-- Bases -----------------------------------------------------------------------
INSERT INTO bases (id, code, name, location, commander) VALUES
  (1, 'BAM', 'Bravo Army Base',   'Northern Sector, Sector 7', 'Brig. R. Sharma'),
  (2, 'CAM', 'Charlie Airbase',   'Eastern Frontier, Airfield 3', 'Col. M. Verma'),
  (3, 'DAM', 'Delta Naval Depot', 'Coastal Command, Dock 5', 'Capt. S. Nair');

-- Equipment types -------------------------------------------------------------
INSERT INTO equipment_types (id, code, name, category, unit, description) VALUES
  (1, 'RIF-556',  '5.56mm Assault Rifle',   'Weapons',      'UNIT',    'Standard issue assault rifle'),
  (2, 'AMMO-762', '7.62mm Ammunition',      'Ammunition',   'BOX',     'Box of 1000 rounds'),
  (3, 'VHF-RD',   'VHF Field Radio',        'Communication','UNIT',    'Man-portable VHF transceiver'),
  (4, 'MED-KIT',  'Combat Medical Kit',     'Medical',      'KIT',     'Advanced trauma first aid kit'),
  (5, 'RAT-24H',  '24H Ration Pack',        'Logistics',    'PACK',    'One soldier-day ration'),
  (6, 'BODY-ARM', 'Body Armour Vest',       'Protection',   'UNIT',    'Level III ballistic vest');

-- Users -----------------------------------------------------------------------
-- BCrypt hashes (strength 10):
--   admin123 / commander123 / logistics123
INSERT INTO users (id, username, password, full_name, email, role_id, base_id, enabled) VALUES
  (1, 'admin',        '$2b$10$WFXZditjh8yzvifTCAjssue4rixFGpj34DD3cjGyMA8dWAX9kwg1G', 'System Administrator', 'admin@mams.mil',     1, NULL, 1),
  (2, 'gen.alpha',    '$2b$10$kVGHAZ.hAE5747EDL5yLWuZxMGsULu9yVKd8fns6JQeaib8XC1u6O', 'Brig. R. Sharma',      'cmd.alpha@mams.mil', 2, 1,    1),
  (3, 'gen.bravo',    '$2b$10$kVGHAZ.hAE5747EDL5yLWuZxMGsULu9yVKd8fns6JQeaib8XC1u6O', 'Col. M. Verma',        'cmd.bravo@mams.mil', 2, 2,    1),
  (4, 'gen.delta',    '$2b$10$kVGHAZ.hAE5747EDL5yLWuZxMGsULu9yVKd8fns6JQeaib8XC1u6O', 'Capt. S. Nair',        'cmd.delta@mams.mil', 2, 3,    1),
  (5, 'logistics',    '$2b$10$Prg7HjPLIouJirSIRkDIo.5ya0bx2vicqSLjAhoA7PthRzazy1U/y', 'Maj. A. Khanna',       'log@mams.mil',       3, NULL, 1),
  (6, 'logistics2',   '$2b$10$Prg7HjPLIouJirSIRkDIo.5ya0bx2vicqSLjAhoA7PthRzazy1U/y', 'Capt. D. Menon',       'log2@mams.mil',      3, NULL, 1);

-- Personnel -------------------------------------------------------------------
INSERT INTO personnel (id, service_number, full_name, rank_title, base_id, contact) VALUES
  (1, 'SN-1001', 'Havildar J. Singh',   'Havildar', 1, '+91-90000-10001'),
  (2, 'SN-1002', 'Naik P. Kumar',       'Naik',     1, '+91-90000-10002'),
  (3, 'SN-2001', 'Sepoy A. Ali',        'Sepoy',    2, '+91-90000-20001'),
  (4, 'SN-2002', 'Havildar T. Rao',     'Havildar', 2, '+91-90000-20002'),
  (5, 'SN-3001', 'Lt. K. Iyer',         'Lieutenant',3,'+91-90000-30001'),
  (6, 'SN-3002', 'Sepoy N. Das',        'Sepoy',    3, '+91-90000-30002');

-- Stock balances (opening balances) -------------------------------------------
INSERT INTO stock_balances (base_id, equipment_type_id, opening_balance, on_hand_quantity) VALUES
  (1, 1, 100, 105),
  (1, 2,  50,  65),
  (1, 3, 200, 145),
  (2, 1,  80, 105),
  (2, 4,  40,  52),
  (2, 5, 120, 110),
  (3, 2,  60,  53),
  (3, 3,  90,  90),
  (3, 4,   0,  10),
  (3, 5,   0,  25),
  (3, 6,  30,  40);

-- Purchases -------------------------------------------------------------------
INSERT INTO purchases (id, reference_no, base_id, supplier, invoice_no, purchase_date, remarks, total_cost, created_by) VALUES
  (1, 'PUR-2026-0001', 1, 'Ordnance Factory Board', 'INV-OFB-5501', '2026-01-10', 'Annual small arms replenishment', 64000.00, 5),
  (2, 'PUR-2026-0002', 2, 'Bharat Electronics Ltd', 'INV-BEL-2210', '2026-01-15', 'Medical and communication stores', 50000.00, 5),
  (3, 'PUR-2026-0003', 3, 'Defence Logistics Corp', 'INV-DLC-7788', '2026-02-01', 'Rations and protective gear',     52500.00, 6);

INSERT INTO purchase_items (purchase_id, equipment_type_id, quantity, unit_cost) VALUES
  (1, 1, 40, 1200.00),
  (1, 2, 20,  800.00),
  (2, 4, 30, 1500.00),
  (2, 1, 10, 1200.00),
  (3, 6, 15, 2000.00),
  (3, 5, 25,  900.00);

-- Transfers -------------------------------------------------------------------
INSERT INTO transfers (id, reference_no, source_base_id, destination_base_id, transfer_date, remarks, created_by) VALUES
  (1, 'TRF-2026-0001', 1, 2, '2026-02-05', 'Reinforcement for eastern frontier', 5),
  (2, 'TRF-2026-0002', 2, 3, '2026-02-12', 'Medical kit redistribution',          6),
  (3, 'TRF-2026-0003', 1, 3, '2026-02-20', 'Ammunition top-up',                    5);

INSERT INTO transfer_items (transfer_id, equipment_type_id, quantity) VALUES
  (1, 1, 15),
  (2, 4, 10),
  (3, 2,  5);

-- Assignments -----------------------------------------------------------------
INSERT INTO assignments (id, base_id, equipment_type_id, personnel_id, quantity, assigned_date, remarks, created_by) VALUES
  (1, 1, 3, 1, 30, '2026-02-06', 'Field radio issue to section',        2),
  (2, 2, 5, 3, 10, '2026-02-07', 'Rations issue for patrol',             3),
  (3, 3, 6, 5,  5, '2026-02-08', 'Body armour issue to boarding team',   4),
  (4, 1, 1, 2, 20, '2026-02-09', 'Assault rifle issue to platoon',       2);

-- Expenditures ----------------------------------------------------------------
INSERT INTO expenditures (id, base_id, equipment_type_id, quantity, expended_date, reason, remarks, created_by) VALUES
  (1, 1, 3, 25, '2026-02-10', 'Battle damage',      'Radios destroyed in field exercise', 2),
  (2, 2, 4,  8, '2026-02-11', 'Medical consumption', 'Kits consumed during training',      3),
  (3, 3, 2, 12, '2026-02-13', 'Training firing',     'Ammunition expended on range',       4);

-- Audit log (opening entry) ---------------------------------------------------
INSERT INTO audit_logs (user_id, username, action, entity, entity_id, base_id, description, created_at) VALUES
  (1, 'system', 'SEED', 'SYSTEM', NULL, NULL, 'Database initialised with seed data', '2026-01-01 00:00:00');

-- =============================================================================
--  Handy views for reporting
-- =============================================================================
CREATE OR REPLACE VIEW v_stock_summary AS
SELECT
  sb.base_id,
  b.code                                   AS base_code,
  b.name                                   AS base_name,
  sb.equipment_type_id,
  et.code                                  AS equipment_code,
  et.name                                  AS equipment_name,
  et.category                              AS category,
  et.unit                                  AS unit,
  sb.opening_balance,
  sb.on_hand_quantity
FROM stock_balances sb
JOIN bases b            ON b.id  = sb.base_id
JOIN equipment_types et ON et.id = sb.equipment_type_id;

-- =============================================================================
--  Test credentials
--   admin       / admin123       -> ADMIN
--   gen.alpha   / commander123   -> BASE_COMMANDER @ Bravo Army Base (id 1)
--   gen.bravo   / commander123   -> BASE_COMMANDER @ Charlie Airbase (id 2)
--   gen.delta   / commander123   -> BASE_COMMANDER @ Delta Naval Depot (id 3)
--   logistics   / logistics123   -> LOGISTICS_OFFICER
--   logistics2  / logistics123   -> LOGISTICS_OFFICER
-- =============================================================================
