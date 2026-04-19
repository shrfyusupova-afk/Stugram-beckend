const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");

const createSupportTicketSchema = {
  body: z.object({
    category: z.enum(["bug", "payment", "account", "chat", "notifications", "content", "other"]),
    subject: z.string().trim().min(3).max(160),
    description: z.string().trim().min(10).max(4000),
    appVersion: z.string().trim().min(1).max(50).optional(),
    deviceInfo: z.string().trim().min(1).max(2000).optional(),
  }),
};

const supportTicketsListQuerySchema = {
  query: paginationQuerySchema,
};

const adminSupportTicketsListQuerySchema = {
  query: paginationQuerySchema
    .extend({
      status: z.enum(["open", "reviewing", "resolved", "rejected"]).optional(),
      category: z.enum(["bug", "payment", "account", "chat", "notifications", "content", "other"]).optional(),
      fromDate: z.string().datetime().optional(),
      toDate: z.string().datetime().optional(),
      search: z.string().trim().min(1).max(120).optional(),
      assignedTo: objectIdSchema.optional(),
    })
    .refine((value) => {
      if (!value.fromDate || !value.toDate) return true;
      return new Date(value.fromDate).getTime() <= new Date(value.toDate).getTime();
    }, {
      message: "fromDate must be before or equal to toDate",
      path: ["fromDate"],
    }),
};

const supportTicketIdParamSchema = {
  params: z.object({
    ticketId: objectIdSchema,
  }),
};

const adminSupportTicketStatusSchema = {
  params: z.object({
    ticketId: objectIdSchema,
  }),
  body: z.object({
    status: z.enum(["reviewing", "resolved", "rejected"]),
  }),
};

const adminSupportTicketAssignSchema = {
  params: z.object({
    ticketId: objectIdSchema,
  }),
  body: z.object({
    assignedTo: objectIdSchema.nullable(),
  }),
};

const adminSupportTicketNoteSchema = {
  params: z.object({
    ticketId: objectIdSchema,
  }),
  body: z.object({
    note: z.string().trim().min(3).max(2000),
  }),
};

module.exports = {
  createSupportTicketSchema,
  supportTicketsListQuerySchema,
  adminSupportTicketsListQuerySchema,
  supportTicketIdParamSchema,
  adminSupportTicketStatusSchema,
  adminSupportTicketAssignSchema,
  adminSupportTicketNoteSchema,
};
