const { z } = require("zod");

const { objectIdSchema } = require("./commonValidators");

const usernameParamSchema = {
  params: z.object({
    username: z.string().trim().min(3).max(30),
  }),
};

const highlightIdParamSchema = {
  params: z.object({
    id: objectIdSchema,
  }),
};

const highlightStoryParamSchema = {
  params: z.object({
    id: objectIdSchema,
    storyId: objectIdSchema,
  }),
};

const highlightCreateSchema = {
  body: z.object({
    title: z.string().trim().min(1).max(30),
    storyIds: z.array(objectIdSchema).min(1).max(20),
    coverStoryId: objectIdSchema.optional(),
  }),
};

const highlightUpdateSchema = {
  body: z.object({
    title: z.string().trim().min(1).max(30).optional(),
    coverStoryId: objectIdSchema.optional(),
  }).refine((value) => value.title !== undefined || value.coverStoryId !== undefined, {
    message: "Provide title or coverStoryId",
  }),
};

const highlightAddStorySchema = {
  body: z.object({
    storyId: objectIdSchema,
    insertAt: z.number().int().min(0).max(99).optional(),
    makeCover: z.boolean().optional(),
  }),
};

module.exports = {
  usernameParamSchema,
  highlightIdParamSchema,
  highlightStoryParamSchema,
  highlightCreateSchema,
  highlightUpdateSchema,
  highlightAddStorySchema,
};
