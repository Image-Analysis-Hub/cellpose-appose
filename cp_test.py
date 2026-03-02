from cellpose import models



model_name = "cyto3"
model = models.CellposeModel(gpu=True)

print("we loaded cellpose v3 default model")